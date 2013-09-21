(ns runner
  (:require [api-user-sim :as user-sim]
            [api-user-agent :as user-agent]
            [db]

            [datomic.api :as d]

            [simulant.sim :as sim]
            [simulant.util :as util]

            [clojure.java.io :as io]
            [clojure.set :refer [union difference]]))

;; ===========================================================
;; Load schemas

(defn load-schema
  [conn resource]
  (let [m (-> resource io/resource slurp read-string)]
    (doseq [v (vals m)]
      (doseq [tx v]
        (d/transact conn tx)))))

;; generic simulation schema
(load-schema db/sim-conn "simulant/schema.edn")

;; schema for this specific sim
(load-schema db/sim-conn "site-sim.edn")

;; ===========================================================
;; model for this sim

(def model-id (d/tempid :model))

(def site-user-model-data
  [{:db/id model-id
    :model/type :model.type/siteUsage
    :model/userCount 100
    :model/meanPayloadSize 2
    :model/meanSecsBetweenHits 10}])

(def site-user-model
  (-> @(d/transact db/sim-conn site-user-model-data)
      (util/tx-ent model-id)))

;; ===========================================================
;; create stuff

;; activity for this sim
(def site-usage-test
  (sim/create-test db/sim-conn site-user-model
                   {:db/id (d/tempid :test)
                    :test/duration (* 10 10000) ;;(util/hours->msec 1)
                    }))

;; sim
(def site-usage-sim
  (sim/create-sim db/sim-conn site-usage-test {:db/id (d/tempid :sim)
                                            :sim/processCount 10}))

;; codebase for the sim
(defn assoc-codebase-tx [entities]
  (let [codebase (util/gen-codebase)
        cid (:db/id codebase)]
    (cons
     codebase
     (mapv #(assoc {:db/id (:db/id %)} :source/codebase cid) entities))))
(d/transact db/sim-conn (assoc-codebase-tx [site-usage-test site-usage-sim]))

;; action log for this sim
(def action-log
  (sim/create-action-log db/sim-conn site-usage-sim))

;; clock for this sim
(def sim-clock (sim/create-fixed-clock db/sim-conn site-usage-sim {:clock/multiplier 960}))

;; ===========================================================
;; run the sim

(comment

  (d/q '[:find ?type
         :where
         [?e :agent/actions ?actions]
         [?e :agent/type ?type]]
       simdb)

  (d/q '[:find ?e
         :where
         [_ :model/tests ?e]
         [?e :test/type ?type]
         ]
       simdb)


  (def pruns
    (->> #(sim/run-sim-process db/sim-uri (:db/id site-usage-sim))
         (repeatedly (:sim/processCount site-usage-sim))
         (into [])))

  ;; wait for sim to finish
  (time
   (mapv (fn [prun] @(:runner prun)) pruns))

  )

;; ===========================================================
;; look at the results

(comment

  ;; grab latest database value so we can validate each of the steps above
  (def simdb (d/db db/sim-conn))




  ;; -----------------
  ;; siteIds for the agents
  ;; .. this is the payloads still in the site (i.e. not removed)

  (def site-ids
    (->> (d/q '[:find ?e
                :where
                [?e :agent/siteIds _]]
              simdb)
         (map first)
         (map #(d/entity simdb %))
         (map :agent/siteIds)
         (apply union)))

  (def site-ids2
    (->> (d/q '[:find ?id
                :where
                [?e :agent/siteIds ?id]]
              simdb)
         ;;(map identity)
         (map first)
         set))

  ;; (->> site-ids2
  ;;      (filter #(contains? #{0 1 2 3 4 6 9 10 11 12 14 15 16} (second %)))
  ;;      (map first)
  ;;      set)

  (count site-ids)
  (count (site/live-ids))
  ;; 167

  (difference site-ids (set (site/live-ids)))
  ;; #{0 1 2 3 4 6 9 10 11 12 14 15 16}

  (defn get-action-site-ids [action-type]
    (->> (d/q `[:find ?id
                :where
                [?e :action/type ~action-type]
                [?e :action/siteId ?id]
                ]
              simdb)
         (map first)
         set))

  (def idmap
    {:put (get-action-site-ids :action.type/put)
     :get (get-action-site-ids :action.type/get)
     :rm  (get-action-site-ids :action.type/delete)})

  (count (difference (:put idmap) (:rm idmap)))
  ;; 153

  (count (site/live-ids))
  ;; 154



  (d/q '[:find ?ids
         :where
         [461794883667827 :agent/siteIds ?ids]]
       simdb)
  (flatten  #{[341] [344] [337] [172] [246] [323] [265] [130]})

  (:agent/siteIds (d/entity simdb 461794883667827))
  ;; #{130 323 265 172 337 341 246 344}




  ;; -----------------
  ;; did we get what we put?

  (defn- get-payload-map [action-type payload-attribute]
    (->> (d/q `[:find ?id ?payload
                :where
                [?e :action/type ~action-type]
                [?e :action/siteId ?id]
                [?e ~payload-attribute ?payload]]
              simdb)
         (map (fn [[id payload]] [id (read-string payload)]))
         (into {})))


  (let [posted-payloads (get-payload-map :action.type/put :action/payload)
        received-payloads (get-payload-map :action.type/get :action/sitePayload)]
    (doseq [[id payload] received-payloads]
      (println id)
      (assert (= payload (posted-payloads id)))))

  (def posted-payloads (get-payload-map :action.type/put :action/payload))
  (def received-payloads (get-payload-map :action.type/get :action/sitePayload))

  (count posted-payloads)
  (count received-payloads)

  (received-payloads 1)
  {"kP{u;)j" "#h"}

  (posted-payloads 1)
  {"kP{u;)j" ["[e%B@eZ~r\\%a}?O@F.C'E#:%" "q\\Q0CM>o_`J+L ik" "Wm#iH.]9epX0HWBWgl>*rzws1mGjig4|6_$@" "1ade,#d)?kZD1J\\`t]vv4<eDXYSU!>vbLJn)>fJpFQlF{>G" "f'e*)):( Iv~vh$(^v,#uM+Km_2D^JbB/eE\\LrT?Tup)Rn<csM/\"E9" "OIij u;y[k~" "tu*" "Pdx+'>k]JqJNyN?D14v[^gY?`UlP md%y9BaP<?7KAu|:L\"JMZ)4B)*fK0Ym9\"04(iO1@a1Kvd]<mt<<I;o!" "LEl-sIcqR" "" "}KlZ0g9!'qXf?L37j##<tt~<DNL-dWK]!9t?cvu\\V;=1g:d0k!'=)8qg^[U;kxT]o2f7W);\\tZeIF-tC?24N[az=x5;xLC0\"\\m89:?B#1$<cZ<'S7!,-A?,rU;\"-3UV<V0w$GYfb@F5EP>X/z@p7:;@JPyJ0S%& Pkkv:|" "w4-\"+'<Q~8bmT,u{i_[+W8V!@}#m)Qu7*/n@ M6iuL_:u70J1U\\O`kT}+G^341qikqb|1[aHwg\\ulaP7d8u'+|x4wYY5vz43s;Y1U|g@Sb_U?]fxRO{jow4BSF/3v^:_-'(7uY.w=2kU,\"9WI-7_17eCaf2e.!c@y{o8!Sc7UNDvnB[JojUqBO0jtmf-=]&78[7+A;{DTWr{Uw/koar` qJ)^Rr;L=d5" "c?zf]-" "a" "`:p%~*Wy<*,#" "R};bL(Ft5{)rRHn8#0RMf_v" "HtqCJ;Q9yyvhc7e$ndIk,@`X'cUTtj4h#+[U5x|\"%^qR7&NOzKxlZ\\@=F]" "RUAH:?>5 G'w@vPe=5IQ7}gE6\\I~Ec6!Cgf/zxQWUk6eQVQx4g|n'y$M69t{nv2PMa]e@-J ~^" "f$$$bU#8<v#97+jG|{1QG34B)vR;hWg!f" "Se=Iz7eJHy{!_9k6AQvTGL|hqw(jFT_=t^OMh#&y7B*g{7~#I/9'LT0[319<}NNt8l9{&lwDs'[{jk\"wMY_,cF9'AT(=TsKp'ah.&~;nHaSO+5R}\\~<8'" "I!J}L mwPR<AUo&nXxSl]&w8wUOQey^S\\Ds#B>7U{LmN)/.}txE;Z)>k.*`g" "PVGx/I9z{VFKA/vIb5TEZ|h6s8ynDZ0pC%0," "_k\"\\Cs-BK40k8<9jT@zx5;Ps" "{%IsEb_T4\\Y'[+b`c" "'`$QhSZ]_BI9IT*T" "tZmjDc!G(-*" " XaA6KC@Fxb:bgM6]]Zax)notCdu4`H6\\Gg\\t{ak o4ngGwt" "94fv~lBav'LBE.9^cKK(|e<'qZ_;`&uj*'>0ZL%oIjUIjS.;{SM6A1M ND%EF_=[l%<,#;MkYqOR:i\">iC:J,G?qF)'hoSY4je~)|r\"S iD>/oSp<a" "PKw6[!ec6Y]wZo4@FmV3|isIjfe-0xSr-l.Ef-09" "!EGKx<@h}F#PI$M'dxZ%3" "@LJIDs# !iY5y66fitT+r%$X`er$r/!1Dg" "=!y\\U$hoG|wKv/@ Z9(SHW'uh~d#y:B6zK=&0HS3+jjF[u\\FU>%EIBv\\$QTBkH&V{VGAdTgMbmMe3h[`7%\"H1@" "GU 3PevK8COKEGo_5>F>&rY'6g4W}kb@dFiKT(=c5rT" "<oSYTb\\{;<6Xj83|8+UG(z{~R@gV6@:Gh2wE35U,%" "d[Gg=Lw+3\"8-sB2#<X$Up&wt]4j6Vf`rdw" "q" "$!TT-UtJ4u#IYz9\\D58CZ:X)8'U8(Vya8#s}PXd#ah{yLH@&em^`pqQN33+^OQSXA>E> +jT0.w|%" "Ae-ta2Et[3'O5d@Cz+c_^&E/~Z8=O?N|=ggSixh275?fs.!4,2M1.x3MM4t>x)Yy\\@]cw}RW|ek+d?f@k;HJ.Yu.;Is:]DcCIcoFgE^`Rz2NY0G\"lB)lyf" "S<OWdvMV4}+Q7j6#oiI}|LDd[l(8%15@CI'r<o+[(Q:\\2}(9P\"CNgGsN" "(@J" "Vbl2'n<cX&Oo2vYPRWeg_=,)$N<ffwx'i;b}j.Wwtzc1>Z>#oApzu$)e" "tCc|Pp\\S[T'1;>Pa>`dx}L^q*bwql,>6n[GjyTa.,-=5TwJ-{y*M{ )Y'e*t^Y?1T(bo\\<1%MFDVxmyrO^RqDzpzZt0,0r#%ON<UB*MjFVX:lvaD1>J" "xEJ[$qc?>lsz7)YU2x`*``" "i$:8n|iNX-e{Qt[?tab|le" "m3g#8G" ",l#9/hzsHeHl5g5.;u0n9zL^j7S]ZyJ=dH}F*q<Ne,iVhIm\")%-{" " &5LvB9el)\"fG8MMoeny5Nkq-oeh $wn0Y$#B`bH^^PH9%Z,,)H&*)Zv ]_{SY7ivjC4k6~*^2\\[.EDAz4Z/_U@hwxm3?6]1w)SdDMY4/1\\f@Q=+4++f|<\"HQL>mEa]!04ZomY/(sLnsmX^NiOsU'IB)F.I:xkSB3SG<4!H+3@\\97]P@iH)Yx9eM_?Yf?TMDd%]C.rO/r%u" "#h"]}







  )
