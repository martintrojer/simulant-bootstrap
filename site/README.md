# simulant-bootstrap

Simulant bootstrap project showcasing how to simulate traffic to a simple API.

This is the site under test

## Usage

`$ lein ring server`

```
$ curl -X GET http://localhost:3000
$ curl -X PUT -H "Content-Type: application/edn" -d '{:name :barnabas}' http://localhost:3000/data
$ curl -X GET http://localhost:3000/data?id=1
$ curl -X GET http://localhost:3000/liveids
$ curl -X DELETE http://localhost:3000/data?id=1
```

## License

Copyright © 2013 Martin Trojer

Distributed under the Eclipse Public License, the same as Clojure.
