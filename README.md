# jdbbackup-docker
A ready to use docker container that schedules and executes the backup of a database.

## How to use it
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

Only proxy is not mandatory. tasks and destinations should not be empty.

The container is able to store the backup in various destinations kind (sftp server, s3, etc...) the format of addresses passed in *destinations* attribute depends on the destination kind. Please have a look below at the **Available destinations** to known where to find documentation.  
More destinations can be added by developing your own plugin. Please have a look at the [jdbbackup-core project](https://github.com/jdbbackup/jdbbackup-core) to know how to do that.

The only database type supported by this container is mySQL. You can add your own (Postgres for example) by developing a *DBDumper* plugin. Please have a look at the [jdbbackup-core project](https://github.com/jdbbackup/jdbbackup-core) to know how to do that.

By default, the path of the file is */tasks.json*. You can define the *TASKS_PATH* environment variable to use another file.
You can easily pass a local file to the image using the --volume docker option:  
```--volume /home/account/path/backupTasks.json:/tasks.json```

### Available sources
- mysql: //TODO

### Available destinations
- file: Saves the backup to a local file to a local file [see jdbbackup-core](https://github.com/jdbbackup/jdbbackup-core).
- sftp: Saves the backup to a sftp server [](). //TODO
- s3: Saves the backup to an [Amazon S3](https://aws.amazon.com/s3/) bucket [](). //TODO
- dropbox: Saves the backup to a [Dropbox](https://www.dropbox.com/) account [](). //TODO

## Adding plugins
To add your own plugins, define the **pluginsDirectory** environment variable and use --volume docker option to mount a host directory at the path defined in **pluginsDirectory**.  
Example: -e -v //TODO

### Plugin registry
//TODO
Set clearDownloadedPlugins system property to true to delete all files in plugin registry.

### Alternate plugin registry
//TODO
private static final String REGISTRY_ROOT_URI = System.getProperty("pluginRegistry", "https://jdbbackup.github.io/webtest/registry/");
final URI url = URI.create(REGISTRY_ROOT_URI+version+".json");

```
{
	"registry": {
		"managers":{
			"sftp":"https://jdbbackup.github.io/webtest/artifacts/jdbbackup-sftp-1.0.0.jar",
			"s3":"https://jdbbackup.github.io/webtest/artifacts/jdbbackup-s3-1.0.0.jar",
			"gcs":"https://jdbbackup.github.io/webtest/artifacts/jdbbackup-gcs-1.0.0.jar",
			"dropbox":"https://jdbbackup.github.io/webtest/artifacts/jdbbackup--1.0.0.jar"
		},
		"dumpers":{}
	}
}
```
Warning absolute URL are mandatory.

## Logging
TODO

# TODO
Remove com.fathzer.jdbbackup.tobedeleted package and src/main/resources/META-INF folder
Detect missing conf attributes