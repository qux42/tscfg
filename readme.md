[![Build Status](https://travis-ci.org/carueda/tscfg.svg?branch=master)](https://travis-ci.org/carueda/tscfg)

## tscfg

tscfg is a command line tool that takes a configuration specification 
parseable by [Typesafe Config](https://github.com/typesafehub/config)
and generates all the Java boiler-plate to make the definitions 
available in type safe, immutable objects.

Typesafe Config is used by the tool for the generation, and required for compilation 
and execution of the generated classes in your code.

### status

This tool was motivated by the lack of something similar to 
[PureConfig](https://github.com/melrief/pureconfig)
but for java (please point me to any that I may have missed!)
It's already usable to some extent but can be improved in several ways
(for example, missing types include lists, durations, ..).
Feel free to play, fork, enter issues, submit PRs, etc.

Of course, avoiding boiler-plate is in general much easier in Scala 
than in Java!
(PureConfig, for example, uses case classes for the configuration spec).

In tscfg's approach, the configuration spec is itself also captured in a configuration file
so the familiar syntax/format (as supported by Typesafe Config) is still used.
With this input the tool generates corresponding POJO classes. 

Why the trouble if you can simply access the properties with Typesafe Config directly? -- see FAQ below.


## configuration spec

It's a regular configuration file where each field value indicates the
corresponding type and, optionally, a default value, for example:

```properties
endpoint {
  # a required String
  path = "string"

  # a String with default value "http://example.net"
  url = "String | http://example.net"

  # an optional Integer with default value null
  serial = "int?"

  interface {
    # an int with default value 8080
    port = "int | 8080"
  }
}
```

Excluding constructors and other methods, this basically becomes 
the immutable class:

```java
public class ExampleCfg {
  public final Endpoint endpoint;
  public static class Endpoint {
    public final String path;
    public final String url;
    public final Integer serial;

    public final Interface interface_;
    public static class Interface {
      public final int port;
    }
  }
}
```

Any value not recognized as an explicit type is still processed by inferring the type according to that value.
So, any existing regular configuration file can still be input to tscfg.

> note: not implemented yet; any unrecognized type is handled as a required string (ie.,
as if "string" was given)

## generation

```shell
$ java -jar tscfg-x.y.z.jar

tscfg x.y.z
Usage:  tscfg.Main --spec inputFile [options]
Options (default):
  --pn packageName  (tscfg.example)
  --cn className    (ExampleCfg)
  --dd destDir      (/tmp)
  --j7 generate code for java <= 7 (8)
  --scala generate scala code  (java)
Output is written to $destDir/$className.ext
```

So, to generate class `tscfg.example.ExampleCfg` with the example above 
saved in `def.example.conf`, we can run:

```shell
$ java -jar tscfg-x.y.z.jar --spec def.example.conf

parsing: def.example.conf
generating: /tmp/ExampleCfg.java
```

## configuration access

Usual Typesafe Config mechanism to load the file, for example:

```java
File configFile = new File("my.conf");
Config tsConfig = ConfigFactory.parseFile(configFile).resolve();
```

Now, to access the configuration fields, instead of:
```java
Config endpoint = tsConfig.getConfig("endpoint");
String path    = endpoint.getString("path");
Integer serial = endpoint.hasPathOrNull("serial") ? endpoint.getInt("serial") : null;
int port       = endpoint.hasPathOrNull("port")   ? endpoint.getInt("interface.port") : 8080;
```

you can:
```java
ExampleCfg cfg = new ExampleCfg(tsConfig);
```
which will make all verifications about required settings and associated types. 
In particular, as is typical with Config use, an exception will be thrown is this verification fails.

Then, while enjoying full type safety and the code completion and navigation capabilities of your IDE:
```java
String path    = cfg.endpoint.path;
Integer serial = cfg.endpoint.serial;
int port       = cfg.endpoint.interface_.port;
```

> note that java keywords are appended "_".

An object reference will never be null if the corresponding field is required according to
the specification. It will only be null if it is marked optional with no default value and
has been omitted in the input configuration.
 
The generated code looks [like this](https://github.com/carueda/tscfg/blob/master/src/main/java/tscfg/example/ExampleCfg.java). 
Example of use [here](https://github.com/carueda/tscfg/blob/master/src/main/java/tscfg/example/Use.java).

## FAQ

**Why the trouble? -- I can just access the configuration values directly with Typesafe Config 
and put them in my own classes**
  
Sure. However, as the number of configuration properties and levels of nesting increase,
the benefits of automated generation of the typesafe, immutable objects, 
along with the centralized verification,
may become more apparent.

**Can tscfg generate `Optional<T>` by default for optional fields?**

Straightforward to add. Feel free to contribute if you are so inclined.
 
**What about generating for Scala?**

Yes, it's there too. Use the `--scala` option and case classes will be generated.
Optional fields are generated with type `Option[T]`.
Looks [like this](https://github.com/carueda/tscfg/blob/master/src/main/scala/tscfg/example/ScalaExampleCfg.scala). 
Example of use [here](https://github.com/carueda/tscfg/blob/master/src/main/scala/tscfg/example/scalaUse.scala).
  

## tests

### java

- [ExampleSpec](https://github.com/carueda/tscfg/blob/master/src/test/scala/tscfg/example/ExampleSpec.scala) 
- [JavaAccessorSpec](https://github.com/carueda/tscfg/blob/master/src/test/scala/tscfg/JavaAccessorSpec.scala)
- [Java7AccessorSpec](https://github.com/carueda/tscfg/blob/master/src/test/scala/tscfg/Java7AccessorSpec.scala)
- [javaIdentifierSpec](https://github.com/carueda/tscfg/blob/master/src/test/scala/tscfg/JavaIdentifierSpec.scala)

### scala

- [ScalaExampleSpec](https://github.com/carueda/tscfg/blob/master/src/test/scala/tscfg/example/ScalaExampleSpec.scala) 
- [scalaAccessorSpec](https://github.com/carueda/tscfg/blob/master/src/test/scala/tscfg/ScalaAccessorSpec.scala)
- [scalaIdentifierSpec](https://github.com/carueda/tscfg/blob/master/src/test/scala/tscfg/scalaIdentifierSpec.scala)
