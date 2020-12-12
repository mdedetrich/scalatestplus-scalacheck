# ScalaTest Plus ScalaCheck
ScalaTest + ScalaCheck provides integration support between ScalaTest and ScalaCheck.

**Usage**

To use it for ScalaTest 3.3.0-SNAP3 and ScalaCheck 1.15.x: 

SBT: 

```
libraryDependencies += "org.scalatestplus" %% "scalacheck-1-14" % "3.3.0.0-SNAP3" % "test"
```

Maven: 

```
<dependency>
  <groupId>org.scalatestplus</groupId>
  <artifactId>scalacheck-1-15</artifactId>
  <version>3.3.0.0-SNAP3</version>
  <scope>test</scope>
</dependency>
```

**Publishing**

Please use the following commands to publish to Sonatype: 

```
$ export SCALAJS_VERSION=0.6.33
$ sbt clean +scalatestPlusScalaCheckJS/publishSigned +scalatestPlusScalaCheckJVM/publishSigned scalatestPlusScalaCheckNative/publishSigned
$ export SCALAJS_VERSION=1.3.0
$ sbt clean +scalatestPlusScalaCheckJS/publishSigned
```
