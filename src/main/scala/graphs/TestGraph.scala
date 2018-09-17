package graphs

import java.util.concurrent.{BlockingQueue, Executors, LinkedBlockingQueue, TimeUnit}

import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph._
import org.apache.tinkerpop.gremlin.structure.{Direction, Vertex}
import org.janusgraph.core.schema.JanusGraphManagement
import org.janusgraph.core.{JanusGraph, JanusGraphFactory, Multiplicity}

import scala.collection.JavaConversions
import scala.io.Source._

/**
  * @author Glennie Helles Sindholt
  */
object TestGraph {

  private val BATCH_SIZE = 50
  private val POOL_SIZE = 4
  private val TIMEOUT_SIXTY_SECONDS = 60

  def load(graph: JanusGraph): Unit = {
    setup(graph.openManagement())
    loadData(graph)
    println("graph data loaded")
  }

  def loadData(graph: JanusGraph): Unit = {
    val vertexCreationQueue = new LinkedBlockingQueue[Runnable]()
    val classLoader = this.getClass.getClassLoader
    val vertexType1Csv = classLoader.getResource("vertexType1.csv")

    val g = graph.traversal()
    vertexCreationQueue.addAll(JavaConversions.seqAsJavaList(fromInputStream(vertexType1Csv.openStream()).getLines.map(line => {
      val entry = line.split(",")
      val vertexDesc = entry(0).toLowerCase
      new VertexType1CreationCommand(vertexDesc, g)
    }).toSeq))

    val vertexType2Csv = classLoader.getResource("vertexType2.csv")
    vertexCreationQueue.addAll(JavaConversions.seqAsJavaList(fromInputStream(vertexType2Csv.openStream()).getLines.map(line => {
      val entry = line.split(",")

      val vertexDesc = entry(0).toLowerCase
      val date = entry(1)
      val prop1 = java.lang.Double.parseDouble(entry(2))
      val prop2 = java.lang.Double.parseDouble(entry(3))
      val prop3 = java.lang.Double.parseDouble(entry(4))
      val prop4 = java.lang.Double.parseDouble(entry(5))

      new VertexType2CreationCommand(vertexDesc, date, prop1, prop2, prop3, prop4, g)
    }).toSeq))

    println("Created vertex commands")
    println("VertexCreationQueue.size()=" + vertexCreationQueue.size())

    val edgeCreationQueue = new LinkedBlockingQueue[Runnable]()
    val edgesCsv = classLoader.getResource("edges.csv")

    edgeCreationQueue.addAll(JavaConversions.seqAsJavaList(fromInputStream(edgesCsv.openStream()).getLines.map(line => {
      val entry = line.split(",")

      val vertexType1 = entry(0)
      val vertexType2 = entry(1)
      val edgeprop1 = entry(2).toLong
      val edgeprop2 = entry(3).toLong
      val date = entry(4)

      new LinkedToEdgeCreationCommand(vertexType1, vertexType2, edgeprop1, edgeprop2, date, g)
    }).toSeq))

    println("EdgeCreationQueue.size()=" + edgeCreationQueue.size())

    var executor = Executors.newFixedThreadPool(POOL_SIZE)
    (0 until POOL_SIZE).foreach(_ => executor.execute(new BatchCommand(g, vertexCreationQueue)))

    executor.shutdown()
    while (!executor.awaitTermination(TIMEOUT_SIXTY_SECONDS, TimeUnit.SECONDS)) {
      println("Awaiting vertex creation..."  + vertexCreationQueue.size())
    }

    executor = Executors.newFixedThreadPool(POOL_SIZE)
    (0 until POOL_SIZE).foreach(_ => executor.execute(new BatchCommand(g, edgeCreationQueue)))

    executor.shutdown()
    while (!executor.awaitTermination(TIMEOUT_SIXTY_SECONDS, TimeUnit.SECONDS)) {
      println("Awaiting edge creation..." + edgeCreationQueue.size())
    }

    println("graph load completed")
  }

  class VertexType2CreationCommand(vertexDesc: String, date: String, prop1: java.lang.Double, prop2: java.lang.Double, prop3: java.lang.Double, prop4: java.lang.Double, g: GraphTraversalSource) extends Runnable {
    def run() {
      g.addV("vertexType2").
        property("desc", vertexDesc.toLowerCase).
        property("date", date).
        property("prop1", prop1).
        property("prop2", prop2).
        property("prop3", prop3).
        property("prop4", prop4).iterate()
    }
  }

  class VertexType1CreationCommand(vertexDesc: String, g: GraphTraversalSource) extends Runnable {
    def run() {
      g.addV("vertexType1").property("desc", vertexDesc.toLowerCase).iterate()
    }
  }

  class LinkedToEdgeCreationCommand(vertexType1Desc: String, vertexType2Desc: String, edgeprop1: Long, edgeprop2: Long, date: String, g: GraphTraversalSource) extends Runnable {
    def run() {
      g.V().hasLabel("vertexType1").has("desc", vertexType1Desc).as("e").
        V().hasLabel("vertexType2").has("desc", vertexType2Desc).addE("linked_to").
        property("edgeprop1", new java.lang.Long(edgeprop1)).
        property("edgeprop2", new java.lang.Long(edgeprop2)).
        property("date", date).
        from("e").iterate()
    }
  }

  class BatchCommand(g: GraphTraversalSource, commands: BlockingQueue[Runnable]) extends Runnable {

    def run() {
      var i = 0
      while (!commands.isEmpty) {
        val command = commands.poll()
        try {
          command.run()
        } catch {
          case e: Throwable  =>
            print("Error processing command:", e.getMessage)
            e.printStackTrace()
            throw new RuntimeException("quitting!")
        }
        i += 1
        if (i % BATCH_SIZE == 0) {
          try {
            var t0 = System.currentTimeMillis()
            g.tx().commit()
            var t1 = System.currentTimeMillis()
            println("Committed batch: " + i/BATCH_SIZE + " Time elapsed: " + (t1 - t0) + " Remaining commands: " + commands.size())
          } catch {
            case e: Throwable =>
              print("Error processing commit {}", e.getMessage)
          }
        }

      }
      try {
        println("committing last batch")
        g.tx().commit()
      } catch {
        case e: Throwable =>
          print("Error processing commit {}", e.getMessage)

      }
    }
  }

  def setup (management: JanusGraphManagement): Unit = {

    if(management.containsGraphIndex("byVertexType1")) {
      println("Database already initialized. Nothing to do here!")
      return
    }
    println("creating indexes")
    val vertexType1Label = management.makeVertexLabel("vertexType1").make
    val vertexType2Label = management.makeVertexLabel ("vertexType2").make

    val linkedToLabel = management.makeEdgeLabel ("linked_to").multiplicity (Multiplicity.MULTI).make

    val descPropKey = management.makePropertyKey ("desc").dataType (classOf[java.lang.String]).make
    val datePropKey = management.makePropertyKey ("date").dataType (classOf[java.lang.String]).make
    val prop1PropKey = management.makePropertyKey ("prop1").dataType (classOf[java.lang.Double]).make
    val prop2PropKey = management.makePropertyKey ("prop2").dataType (classOf[java.lang.Double]).make
    val prop3PropKey = management.makePropertyKey ("prop3").dataType (classOf[java.lang.Double]).make
    val prop4PropKey = management.makePropertyKey ("prop4").dataType (classOf[java.lang.Double]).make
    val edgeprop1PropKey = management.makePropertyKey ("edgeprop1").dataType (classOf[java.lang.Integer]).make
    val edgeprop2PropKey = management.makePropertyKey ("edgeprop2").dataType (classOf[java.lang.Long]).make

    management.buildIndex("byVertexType1", classOf[Vertex]).addKey(descPropKey).indexOnly(vertexType1Label).buildCompositeIndex
    management.buildIndex("byVertexType2", classOf[Vertex]).addKey(descPropKey).indexOnly(vertexType2Label).buildCompositeIndex
    management.buildEdgeIndex(linkedToLabel, "linkedToByDate", Direction.OUT, Order.decr, datePropKey)
    management.buildEdgeIndex(linkedToLabel, "linkedToByProp1", Direction.BOTH, Order.decr, edgeprop1PropKey)
    management.commit()
  }

}
