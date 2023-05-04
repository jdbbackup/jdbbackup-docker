![Maven Central](https://img.shields.io/maven-central/v/com.fathzer/jdbbackup-docker)
![License](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=jdbbackup_jdbbackup-docker&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=jdbbackup_jdbbackup-docker)
[![javadoc](https://javadoc.io/badge2/com.fathzer/jdbbackup-docker/javadoc.svg)](https://javadoc.io/doc/com.fathzer/jdbbackup-docker)

# jdbbackup-docker
A ready to use docker container, based on [jdbbackup-core](https://github.com/jdbbackup/jdbbackup-core), that schedules and executes the backup of a data sources.

## How to use it

### Without Docker
This application requires Java11+.

The [artifact deployed in Maven central](https://repo1.maven.org/maven2/com/fathzer/jdbbackup-docker/1.0.0/jdbbackup-docker-1.0.0.jar) is a runnable jar.  
Launch it with ```java -jar jdbbackup-docker-1.0.0.jar config.json``` where *config.json* is the configuration file ([see below](#configuration-file)) or set the environment variable *TASKS_PATH* to the path of the configuration file and launch it with ```java -jar jdbbackup-docker-1.0.0.jar```. You may also leave *TASKS_PATH* unset, its default value is *task.json*.

If you want to include this application in a Java program, the main class is *com.fathzer.jdbbackup.cron.Main*.

In order to use the MySQL source, *mysqldump* command must be installed on the machine taht runs this application.

### With Docker
**TODO**
By default, the path of the [configuration file](#configuration-file) is */tasks.json*. You can define the *TASKS_PATH* environment variable to use another file

You can also easily pass a local file to the image using the --volume docker option: 
```--volume /home/account/path/backupTasks.json:/tasks.json```


### Configuration file
You should provide a json configuration file with the following format

```
{
  "proxy":"[user[:pwd]@]@host::port",
  "tasks": [{
  	"name":"Mybackup",
  	"schedule":"@daily"
  	"source":"mysql://root:pwd@host:port/database",
  	"destinations":["s3://bucket/path"]}]
}
```

JSon attributes:  
- proxy: The proxy to used to connect to remote servers. This attribute is not mandatory. *pwd* and *user* are optional in this attribute.
- tasks: The list of backup tasks. This attribute is mandatory and should not be empty.
  - name: The task's name.
  - schedule: The task's schedule. This attribute accepts [*cron-like* patterns](https://www.sauronsoftware.it/projects/cron4j/manual.php#p02) and the following values:
    - @hourly: Every hour on the hour.
    - @daily: Every day at midnight.
    - @monthly: Every month the first day of the month at midnight.
    - @yearly: Every year the first day of the year at midnight.
  - source: The data source to backup.
  - destinations: The destinations where to save the data. It can't be empty.e
The container is able to store the backup in various destinations kind (sftp server, s3, etc...) the format of addresses passed in *destinations* attribute depends on the destination kind.  
The only data source type included in this container is mySQL. You can add your own (Postgres for example) by developing a *SourceManager* plugin.  
Please have at [jdbbackup-core project](https://github.com/jdbbackup/jdbbackup-core) to find documentation on existing source and destination managers, and to learn how to develop your own. 

## Adding plugins
To add your own plugins, define the **pluginsDirectory** environment variable and use --volume docker option to mount a host directory at the path defined in **pluginsDirectory**.  
Example: ```-e "pluginsDirectory=/plugins" --volume /home/account/path/plugins:/plugins``̀`

### Plugin repository
This image only contains MySQL database source manager and file destination manager. If another source/destination is referenced in the configuration file, without being added through the **pluginsDirectory**, the container automatically search it in an Internet plugin repository.  

If you want to delete all already downloaded plugins and reload useful ones at container startup, set the **clearDownloadedPlugins** system property to true.

### Alternate plugin repository
By default, the image uses the plugin repository whose root URI is https://jdbbackup.github.io/web/repository/.  
The full URL is completed with the image version, for instance [https://jdbbackup.github.io/web/repository/1.0.0](https://jdbbackup.github.io/web/repository/1.0.0).

If you want to use your own repository, put its root URI in **pluginRepository** system property.  
Your repository should return a json file like the following at the address *root*/*version*.

```
{
	"repository": {
		"destinationManagers":{
			"sftp":"https://myOwnRepo.com/artifacts/jdbbackup-sftp-1.0.0.jar",
			"s3":"https://myOwnRepo.com/artifacts/jdbbackup-s3-1.0.0.jar",
			"gcs":"https://myOwnRepo.com/artifacts/jdbbackup-gcs-1.0.0.jar",
			"dropbox":"https://myOwnRepo.com/artifacts/jdbbackup-dropbox-1.0.0.jar"
		},
		"sourceManagers":{
			"fake":"https://www.astesana.net/jdbbackup/artifacts/jdbbackup-fakesource-1.0.0.jar"
		}
	}
}
```
Warning absolute URL are mandatory.

## Logging
Logging is based on the [slf4j framework](https://www.slf4j.org/). Logs are bound with [LogBack](https://logback.qos.ch/manual/).  
The default configuration logs to the console, rejecting entries below *info* level.  
If you want to change logback configuration, please have a look at [the logback manual](https://logback.qos.ch/manual/configuration.html).

# TODO
Detect missing configuration attributes  
Verify destination is valid regarding validate method