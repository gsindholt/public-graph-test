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
