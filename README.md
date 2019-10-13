# mgw-spring-plugin
Plugin to generate a microgateway for the spring services

#### Sample 

```
    <plugin>
        <groupId>org.wso2.am.microgw</groupId>
        <artifactId>mgw-spring-maven-plugin</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <executions>
            <execution>
                <phase>package</phase>
                <goals>
                    <goal>add-gateway</goal>
                </goals>
            </execution>
        </executions>
        <configuration>
            <packageName>com.example.spring.project</packageName>
            <toolkitHome><MICRO_GATEWAY_TOOLKIT_PATH></toolkitHome>
        </configuration>
    </plugin>
```
