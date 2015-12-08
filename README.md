This project includes a sample Fedora dataset and a tool to upload the dataset into a Fedora repository for demostration.

To import the sample dataset:

1. Download the fcrepo-sample-dataset tool: git clone git@github.com:futures/fcrepo-sample-dataset.git
2. Change to the fcrepo-sample-dataset directory: cd fcrepo-sample-dataset
3. Build: mvn clean install
4. Run: mvn -Dfcrepo.url=&lt;repo.url&gt; exec:java

The whole dataset will be loaded into the Fedora repository <repo.url>, or view the dataset from http://localhost:8080/rest by default. 

### Authentication
For a secure repo, the authentication information can be set using the following properties: ```fcrepo.authUser``` and ```fcrepo.authPassword``` command-line properties. 

### Default Profile
To load your own dataset, convert it to jcr/xml format and place the data files under src/main/resources/data directory, then run ```mvn -Dfcrepo.url=&lt;repo.url&gt; exec:java``` to load it to the repository.

### Resource Import Profile
The resource import profile supports loading turtle files and performing SPARQL updates on the repository. The ```resourceimport``` maven profile loads the data (only ttl and ru files) at the ```src/main/resources/data``` location by default. This can be overridden by specifying a location using ```resources.dir``` as depicted in the example below:

```
mvn exec:java -P resourceimport -Dfcrepo.url=http://localhost:8080/rest/sample-set -Dfcrepo.authUser=user -Dfcrepo.authPassword=pass -Dresources.dir=/path/to/data
```

The above command will load data from location ```/path/to/data``` to ```http://localhost:8080/rest/sample-set``` creating containers for the subdirectories and resources for the .ttl files. The sparql updates in the .ru files will be applied after the containers and resources are created.