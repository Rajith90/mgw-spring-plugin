# mgw-spring-plugin
Plugin to generate a WSO2 microgateway for spring services.

Download the toolkit for the microgateway.
Then add the following plugin for the spring project. 

#### Sample 

```
    <plugin>
        <groupId>org.wso2.am.microgw</groupId>
        <artifactId>mgw-spring-maven-plugin</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <executions>
            <execution>
                <id>build-gateway</id>
                <phase>package</phase>
                <goals>
                    <goal>add-gateway</goal>
                </goals>
            </execution>
        </executions>
        <configuration>
            <toolkitHome>/Users/rajithroshan/Documents/spring/packs/wso2am-micro-gw-toolkit-3.0.2-SNAPSHOT</toolkitHome>
            <buildProject>
                <openAPIName>petstore_basic.yaml</openAPIName>
                <packageName>com.example.swagger</packageName>
                <processProject>true</processProject>
            </buildProject>
        </configuration>
    </plugin>
```
#### Configuration for `configuration`

| **Main Config** | **Secondary Config** | **description** |
|-----------------|----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `toolkitHome`   | N/A                  | The directory path to the WSO2 Microgateway toolkit  |
| `buildProject`  | `openAPIName`        | The name of the open API file present in the project resources directory. If provided these endpoint also will be added to micro gateway |
|                 | `packageName`        | The root package name of the spring project in which the REST services are defined |
|                 | `processProject`     | If the `openAPIName` is provided, then spring project will only be processed if this is set to `true`. If `openAPIName` is not provided then irrespective of this value spring project will be processed to build the gateway |