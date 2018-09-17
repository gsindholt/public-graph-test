# public-graph-test

## Build
Fat jar including data files has already been build and is located in the root of this project. To build it yourself use `sbt assembly`

## Usage
Janusgraph must already have been configured. I use DynamoDB as my backend and I have used this guide https://bricaud.github.io/personal-blog/janusgraph-running-on-aws-with-dynamodb/ to set it up.

1) Add fat-jar to `dynamodb-janusgraph-storage-backend/server/dynamodb-janusgraph-storage-backend-1.2.0/lib/`. 
2) Restart gremlin-server
3) Start gremlin console
4) Connect to server and load data:  
```
:remote connect tinkerpop.server conf/remote.yaml session
:remote console
graphs.TestGraph.load(graph)
```
## Execution via console
My test case queries 4 specific node. The IDs of those nodes are obtained by this query:
```
ids = g.V().hasLabel('vertexType1').has('desc', within('fb735e78-4f5e-4ea2-977a-4bad75afa547','a3c53472-86a6-4ebe-a740-dccd7a07fb35','d09714ec-62a9-42e2-94b5-9aff627aa806','93b6f9e0-edc2-4343-bc49-42a42e2e620c')).id().fold()
```

The following query is then executed at least twice to ensure that the script is cached
```
g.V(ids).out().in().dedup().count().profile()
```

## Execution via HTTP
cURL is used to query via HTTP. I execute the query from the server to avoid network delays and it is executed a couple of times to make sure the script is cached:

```
curl -XPOST -Hcontent-type:application/json -d '{"gremlin":"g.V(ids).out().in().dedup().count().profile()", "bindings":{"ids":[insert obtained ids here]}}' http://10.0.1.100:8182
```
