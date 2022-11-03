


# 编译命令

在flink的目录下运行：

```shell
mvn clean package -DskipTests -Pvendor-repos -Drat.skip=true -Dmaven.javadoc.skip=true -Dcheckstyle.skip=true
```


# Flink集群启动




# Flink任务提交

任务提交时运行的主类：

```shell
org.apache.flink.client.cli.CliFrontend
```



