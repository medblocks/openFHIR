Package your plugin as a jar file and place it in the `plugins` directory. The plugin will be loaded automatically when the application starts.

eg:

```
cd test-plugin-project
mvn clean package
mv target/test-plugin-1.0.0.jar ../plugins

```
