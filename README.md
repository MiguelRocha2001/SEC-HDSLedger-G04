# HDSLedger

## Introduction

HDS Serenity Ledger is a simplified permissioned (closed membership) blockchain system with high dependability
guarantees. It uses the Istanbul BFT consensus algorithm to ensure that all nodes run commands
in the same order, achieving State Machine Replication (SMR) and guarantees that all nodes
have the same state. 
Overall, the system allows the clients to perform crypto exchanges, as well as consulting their current account balance.

## Requirements

- [Java 17](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html) - Programming language;

- [Maven 3.6](https://maven.apache.org/) - Build and dependency management tool;

- [Python 3](https://www.python.org/downloads/) - Programming language;

---

# Configuration Files

### Node and Client configuration

Can be found inside the `resources/` folder.

Node:
```json
{
    "id": <NODE_ID>,
    "hostname": "localhost",
    "port": <NODE_PORT>,
    "clientPort": <CLIENT_NODE_PORT>,
    "byzantineBehavior": <BYZANTINE_BEHAVIOR>
}
```

Client:
```json
{
    "id": <NODE_ID>,
    "hostname": "localhost",
    "port": <NODE_PORT>,
    "byzantineBehavior": <BYZANTINE_BEHAVIOR>
}
```

There is a set of possible node configurations, and only one for the client: **clientConfig.json**.

Note: the configuration can be changed in the **puppet-master.py** file.

## Dependencies

### Script

To install the necessary dependencies run the following command:

```bash
./install_deps.sh
```

This should install the following dependencies:

- [Google's Gson](https://github.com/google/gson) - A Java library that can be used to convert Java Objects into their JSON representation.

### Authentication Keys (Public/Private keys)

For each process that is specified in the json configuration files, there should exist a pair of public - private keys, in the form of public<ID>.key and private<ID>.key, <ID> being the id of the respective process. This keys should stay in [keys](HDSLedger/resources/keys) folder.

Also, if the client wishes to use a key that is not loaded/known by the replicas/nodes, there is a pair of Public/Private keys inside the (HDSLedger/resources/unregistered) folder.

The keys could be generated with the ***RSAKeyGenerator.java*** class, available in the [RSAKeyGenerator](HDSLedger/Communication/src/main/java/pt/ulisboa/tecnico/hdsledger/communication/cripto/RSAKeyGenerator.java)

## Puppet Master

The puppet master is a python script `puppet-master.py` which is responsible for starting the nodes and clients of the blockchain.
The script runs with `kitty` terminal emulator by default since it's installed on the RNL labs.

To run the script you need to have `python3` installed.
The script has arguments which can be modified:

- `terminal` - the terminal emulator used by the script
- `server_config` - a string from the array `server_configs` which contains the possible configurations for the blockchain nodes

Run the script with the following command:

```bash
python3 puppet-master.py
```
Note: You may need to install **kitty** in your computer

## Maven

It's also possible to run the project manually by using Maven.

### Instalation

Compile and install all modules using:

```
mvn clean install
```

### Execution

Run without arguments

```
cd <module>/
mvn compile exec:java
```

Run with arguments

```
cd <module>/
mvn compile exec:java -Dexec.args="..."
```

# Tests

To run the tests on the [puppet-master.py](HDSLedger/puppet-master.py) you choose a test by setting var `server_configs` line 27 index from 0 to 4.

Index | Test 
-----|-----
0 | Correct Configurations
1 | Ignore Requests Configurations
2 | Bad Leader Propose With Generated Signature
3 | Bad Leader Propose With Original Signature
3 | Upon Prepare Quorum Wrong Value
4 | Upon Round Change Quorum Wrong Value
5 | Fake Leader With Forged Pre-Prepare
6 | Dont Validate Transaction
7 | Fake Consensus Instance
8 | Force Round Change

---

# Report

The report for this project, can be found [here](docs/Report.pdf)

# Disclaimer
This project was part of the IST MEIC SEC course.
