jacoco-scala-maven-plugin [![Build Status](https://travis-ci.org/timezra/jacoco-scala-maven-plugin.png)](https://travis-ci.org/timezra/jacoco-scala-maven-plugin)
==================

A maven plugin for creating filtered JaCoCo code coverage reports for Scala. Please read the companion blog entry (http://timezra.blogspot.com/2013/10/jacoco-and-scala.html) for more information on how to use this plug-in.

Configuration
----------------------------------------------------
In order to use this Maven plugin, you will need to configure your pom.xml (or proxy repository) to point to the repository at <http://timezra.github.com/maven/releases>

```xml
    <pluginRepositories>
      <pluginRepository>
        <id>tims-repo</id>
        <url>http://timezra.github.com/maven/releases</url>
        <releases>
          <enabled>true</enabled>
        </releases>
        <snapshots>
          <enabled>false</enabled>
        </snapshots>
      </pluginRepository>
    </pluginRepositories>
```

Usage
----------------------------------------------------
Work in progress....

Used the same way as org.jacoco:jacoco-maven-plugin:report, except you can can specify scala-specific optional filters defined here: https://github.com/jacoco/jacoco/wiki/FilteringOptions
Currently SCALAC.MIXIN and SCALAC.CASE are supported.
For now, please use 'mvn timezra.maven:jacoco-scala-maven-plugin:0.6.3.1:help' to find out usage information.

### Examples: ###

```xml
    <plugin>
      <groupId>org.jacoco</groupId>
      <artifactId>jacoco-maven-plugin</artifactId>
      <version>0.6.3.201306030806</version>
      <executions>
        <execution>
          <id>pre-test</id>
          <goals>
            <goal>prepare-agent</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
    <plugin>
      <groupId>timezra.maven</groupId>
      <artifactId>jacoco-scala-maven-plugin</artifactId>
      <version>0.6.3.1</version>
      <executions>
        <execution>
          <id>post-integration-test</id>
          <phase>post-integration-test</phase>
          <goals>
            <goal>report</goal>
          </goals>
          <configuration>
            <filters>
              <filter>SCALAC.MIXIN</filter>
              <filter>SCALAC.CASE</filter>
            </filters>
          </configuration>
        </execution>
      </executions>
    </plugin>
```

