# jdbbackup-docker
A ready to use docker container that schedules and executes the backup of a data sources.

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

The only data source type included in this container is mySQL. You can add your own (Postgres for example) by developing a *SourceManager* plugin. Please have a look at the [jdbbackup-core project](https://github.com/jdbbackup/jdbbackup-core) to know how to do that.

By default, the path of the file is */tasks.json*. You can define the *TASKS_PATH* environment variable to use another file.
You can easily pass a local file to the image using the --volume docker option:  
```--volume /home/account/path/backupTasks.json:/tasks.json```

### Available sources
- mysql: Dumps the whole content of a mySQL Database ([see jdbbackup-core](https://github.com/jdbbackup/jdbbackup-core))

### Available destinations
- file: Saves the backup to a local file to a local file ([see jdbbackup-core](https://github.com/jdbbackup/jdbbackup-core)).
- sftp: Saves the backup to a sftp server [see jdbbackup-sftp](https://github.com/jdbbackup/jdbbackup-sftp).
- s3: Saves the backup to an [Amazon S3](https://aws.amazon.com/s3/) bucket [see jdbbackup-s3](https://github.com/jdbbackup/jdbbackup-s3).
- dropbox: Saves the backup to a [Dropbox](https://www.dropbox.com/) account [jdbbackup-dropbox](https://github.com/jdbbackup/jdbbackup-s3).

## Adding plugins
To add your own plugins, define the **pluginsDirectory** environment variable and use --volume docker option to mount a host directory at the path defined in **pluginsDirectory**.  
Example: ```-e "pluginsDirectory=/plugins" --volume /home/account/path/plugins``̀`

### Plugin registry
This image only contains MySQL database source manager and file destination manager. If another source/destination is referenced in the configuration file, without being added through the **pluginsDirectory**, the container automatically search it in an Internet plugin registry.  

If you want to delete all already downloaded plugins and reload useful ones at container startup, set the **clearDownloadedPlugins** system property to true.

### Alternate plugin registry
By default, the image uses the plugin registry whose root URI is https://jdbbackup.github.io/web/registry/.  
The full URL is completed with the image version, for instance [https://jdbbackup.github.io/web/registry/1.0.0](https://jdbbackup.github.io/web/registry/1.0.0).

If you want to use your own registry, put its root URI in **pluginRegistry** system property.  
Your registry should return a json file like the following at the address *root*/*version*.

```
{
	"registry": {
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