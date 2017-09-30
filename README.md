# Akka-File-Sharing
File sharing distributed application developed with Akka Framework.

This application can share files in a distributed fashion among different nodes in a network. 
No centralized entities involved, this is a P2P application.
Akka Framework is used to handle the decentralized and fully distributed architecture.

A distributed hash table is created among the nodes, so that a distributed lookup for the files can be performed.
The load balancing builtin functionality helps distributing files in a uniform manner over the participating nodes.

Configuration
-------------
In order to let the application run correctly, the configuration must be changed.
In Netbeans, navigate to Run->Set Project Configuration->Customize. Configuration is under the Run tab.

Mandatory changes:
* Dapp-settings.net-interface: must be set to the name (even a part of the name) of the network interface on which the application is listening (e.g. "Qualcomm Atheros Wi-Fi" or "Hamachi"). For a complete list of net interface names, run "ipconfig /all" command.

* Dakka.cluster.seed-nodes.0: the seed node address must be a known active node in the cluster, so that, on node startup, the join to the cluster is performed connecting to the seed-node. If the new node is alone in the cluster (this is the only active instance of the application), the node must work itself as seed-node; therefore:
	* the seed-node ip must be set to the address assigned to the used net-interface
	* the seed-node port must be equal to the one specified in parameter -Dakka.remote.netty.tcp.port (default is 2551)
 
