# Object Flattener
A java library which helps you flatten java object to single-layer-dot-annotated properties and back to json and/or java object.

## Why properties?
* This is helpful when we need to convert a Java object to config properties file or backwards;
* Or, if you're planning to write structured logs in JSON line, it will be handy to store key-value pairs in MDC and then unflatten them into object and eventually format it to JSON line.

## Examples
For instance if you have a Java class defined like this:
```java
public class User {

  private final String username;
  private final String password;
  private final List<String> roles;
  
  @JsonCreator
  public User(
    @JsonProperty("username") final String username,
    @JsonProperty("password") final String password,
    @JsonProperty("roles") final List<String> roles
  ) {
    // Details are omitted
  }
  
  // Getters are omitted
}
```
You can call `ObjectFlattener.flatten()` to get a flattened single-layer Map like this:
```java
Map<String, String> flattened = ObjectFlattener.flatten(
  new User("John", "Dow", Arrays.asList("User", "Reporter"))
);
```
The content on the map will be:
```properties
username=John
password=Dow
roles.[0]=User
roles.[1]=Reporter
```
You can also unflatten it to JsonNode:
```java
  JsonNode node = ObjectFlattener.unflatten(flattened);
```
or Java object:
```java
  User user = ObjectFlattener.unflatten(flattened, User.class);
```

## Advanced Usage
* You can pass your own `ObjectMapper` in case you want additional controll of the serialization/deserialization process.
* You can pass a prefix string when flattening to append prefix to generated properties.
* You can pass a prefix string when unflattening so you could only read a subset of the properties. 

## License

    Copyright 2020-2020 SgrAlpha
   
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
   
       http://www.apache.org/licenses/LICENSE-2.0
   
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
