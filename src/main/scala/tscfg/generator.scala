package tscfg

import java.io.{PrintWriter, Writer}
import java.util.Date

import com.typesafe.config.{Config, ConfigRenderOptions, ConfigValue}

import scala.collection.JavaConversions._
import scala.collection.mutable


object generator {

  def java(config: Config,
           packageName: String,
           className: String,
           out: Writer
          ): Unit = {

    createAllNodes(config)

    val pw = out match {
      case w: PrintWriter => w
      case w => new PrintWriter(w)
    }
    genJava(Node.root, packageName, className, pw)
  }

  // we first traverse the list of elements to build a tree representation.

  sealed abstract class Node {
    val key: Key
  }

  case class LeafNode(key: Key, value: ConfigValue) extends Node {
    val accessor = Accessor.parseValueSpec(value.unwrapped().toString)
  }

  case class BranchNode(key: Key) extends Node {
    val map: mutable.Map[String,Node] = mutable.Map()

    def put(simpleKey: String, node: Node): Unit = {
      map.put(simpleKey, node)
    }
  }

  object Node {
    val root = BranchNode(Key.root)
  }


  object nodes {
    val nodeMap = mutable.HashMap[Key, Node]()

    put(Key.root, Node.root)

    // creates a leaf node
    def createLeaf(key: Key, value: ConfigValue): LeafNode = nodeMap.get(key) match {
      case None =>
        val node = LeafNode(key, value)
        put(key, node)
        node

      case Some(node) => throw new Error(s"Node by key=$key already created")
    }

    // creates a branch node
    def createBranch(key: Key): BranchNode = nodeMap.get(key) match {
      case None =>
        val node = BranchNode(key)
        put(key, node)
        node

      case Some(node) if node.isInstanceOf[BranchNode] => node.asInstanceOf[BranchNode]
      case Some(node) => throw new Error(s"LeafNode by key=$key already created")
    }

    private def put(key: Key, node: Node) = {
      //println(s"nodeMap PUT: $key -> $node")
      require(!nodeMap.contains(key))
      nodeMap.put(key, node)
    }
  }

  private def createAllNodes(conf: Config): Unit = {
    conf.entrySet() foreach { e =>
      val key = Key(e.getKey)
      val value = e.getValue
      val leafNode = nodes.createLeaf(key, value)
      createAncestorsOf(key, leafNode)
    }
  }

  private def createAncestorsOf(childKey: Key, childNode: Node): Unit = {
    createParent(childKey.parent, childNode)

    def createParent(parentKey: Key, child: Node): Unit = {
      val parentNode = nodes.createBranch(parentKey)
      parentNode.put(child.key.simple, child)

      if (parentNode.key != Key.root)
        createParent(parentKey.parent, parentNode)
    }
  }

  private def showNode(node: Node, indentIncr:String = "\t", simpleKey: Boolean = true): Unit = {
    show(node)

    def show(n: Node, indent: String = ""): Unit = {
      val label = if (simpleKey) n.key.simple else n.key.toString

      n match {
        case LeafNode(key, value) =>
          println(s"${indent}$label = ${value.render()}")

        case n: BranchNode =>
          println(s"${indent}$label")
          n.map.keys.toList.sorted foreach { key =>
            show(n.map(key), indent + indentIncr)
          }
      }
    }
  }

  private def genJava(node: Node, packageName: String, className: String, out: PrintWriter): Unit = {
    out.println(s"// auto-generated by tscfg on ${new Date()}\n")
    out.println(s"package $packageName;\n")
    out.println(s"import com.typesafe.config.Config;\n")

    val indentIncr = "  "

    gen(node)

    def gen(n: Node, indent: String = ""): Unit = {
      val simple = n.key.simple
      val isRoot = simple == "/"
      val symbol = if (simple == "/") className else simple

      val javaId = javaIdentifier(symbol)

      n match {
        case ln@LeafNode(key, value) =>
          out.println(s"${indent}public final ${ln.accessor.javaType} $javaId;")

        case n: BranchNode =>

          val className = upperFirst(symbol)

          if (!isRoot) {
            // declare symbol:
            out.println(s"${indent}public final $className $javaId;")
          }

          // declare class:

          val classDecl = if (isRoot) "class" else "static class"
          out.println(s"${indent}public $classDecl $className {")

          val newIndent = indent + indentIncr

          val orderedNames = n.map.keys.toList.sorted

          orderedNames foreach { name =>
            gen(n.map(name), newIndent)
          }

          // constructor:

          out.println(s"${newIndent}public $className(Config c) {")

          orderedNames foreach { name =>
            out.print(s"${newIndent}  this.${javaIdentifier(name)} = ")
            n.map(name) match {
              case ln@LeafNode(k, v) =>
                val path = k.simple
                //val path = k.toString
                val instance = ln.accessor.instance(path)
                val comment = v.render(ConfigRenderOptions.concise())
                out.println(s"""$instance; // $comment """)

              case BranchNode(k)  =>
                val className = upperFirst(k.simple)
                out.println(
                  if (isRoot)
                    s"""new $className(c.getConfig("${k.simple}"));"""
                  else {
                    s"""new $className(c.getConfig("${k.simple}"));"""
                  }
                )
            }
          }

          out.println(s"${newIndent}}")

          // toString():
          if(isRoot) {
            out.println(s"""${newIndent}public String toString() { return toString(""); }""")
          }

          // toString(String indent):
          out.println(s"${newIndent}public String toString(String ind) {")

          val ids = orderedNames map { name =>
            val id = javaIdentifier(name)

            n.map(name) match {
              case ln@LeafNode(k, v) =>
                (if(ln.accessor.javaType == "String") {
                  s"""ind + "$id = " + (this.$id == null ? null : '"' + this.$id + '"')"""
                }
                else {
                  s"""ind + "$id = " + this.$id"""
                }) + s""" + "  // ${ln.accessor.javaType}\\n" """

              case BranchNode(k) =>
                s"""ind + "$id = {\\n" + this.$id.toString(ind + "    ") + ind + "}\\n" """
            }
          }

          out.println(s"${newIndent}return")
          out.println(s"${newIndent}    ${ids.mkString("\n" +newIndent + "   +")};")
          out.println(s"${newIndent}}")
          out.println(s"${indent}}")
      }
    }
  }

  // appends a _ in case the symbol is a java reserved word (not fully implemented)
  private def javaIdentifier(symbol:String) = {
    val javaReservedWords: Set[String] = Set("class", "public", "interface")

    if (javaReservedWords.contains(symbol)) symbol + "_" else symbol
  }

  private def upperFirst(symbol:String) = symbol.charAt(0).toUpper + symbol.substring(1)

}