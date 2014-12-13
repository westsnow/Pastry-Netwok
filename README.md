Pastry-Netwok
=============

built a pastry network using distributed hash table.

To run the program, there are two parameters to provide.
- 1. nodeNum in the network
- 2. requestNum each node is supposed to send.
```
For example: “java -jar pastry.jar 1000 5” startup a pastry network of 1000 nodes. After the network is built, 
every second, each node send a request to a random node, until they sends 5 requests.
Then the output tells us the average hop of each request.
```

**pastry.pdf is an academic paper about pastry network by Microsoft Reasearch.**
