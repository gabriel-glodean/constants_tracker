
# Constant Tracker

This is pretty much a toy project for me to test out Java 25. For now, it can analyze a subset of bytecode to figure out how constants are being used and store the info into Solr. It uses Webflux for handling REST calls. 


## License

[MIT](https://choosealicense.com/licenses/mit/)

## Running
The easiest way to starting the application up is to use Terraform, for this the following steps are
required:
1. Install Gradle (9.1.0+ for JAVA 25 support), Docker, and Terraform.
2. Call  _docker build -t constant_tracker:latest ._
3. Call _terraform init_
4. Call _terraform apply_ to start the Tracker, Redis, and Solr
5. Call _terraform destroy_ to stop them.

***NOTE*** I know that step to shouldn't be there, but for now I will live with it.

To send REST request I recommend using Postman (or something similar) as it makes sending class files trivial, just selecting them and that's it)

For now, the only endpoint supported is class with GET, PUT, and POST verbs implemented.
A sample url is _localhost:8080/class?version=1&project=test&className=org/glodean/constants/samples/Greeter_.
For POST and PUT make sure that your requests include Content-Type:application/octet-stream

## Java package structure
- __web.endpoints__: contains the reactive endpoint definitions
- __store__: classes for writing into Solr
- __model__: general extracted data
- __extractor__: classes for extracting constants from various places (for now just bytecode)
- __extractor__: classes that implement a Points-to Analysis on JVM bytecode to extract constants and how they are used.
- __dto__: for entering and exiting the app.