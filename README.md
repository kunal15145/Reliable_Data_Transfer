# Go Back N and its features

1. Cumulative Ack done using Go back n and its reciever
2. Sequence number: same as in TCP
3. Retransmissions
4. Detecting duplicates and discarding them.
5. Congestion control (TCP Reno)
6. Flow Control

# Selective Repeat and its features

1. Cumulative Ack done using Go back n and its reciever
2. Sequence number: same as in TCP
3. Retransmissions
4. Detecting duplicates and discarding them.


Packet Drop in linux with 30% probability

sudo iptables -A INPUT -m statistic --mode random --probability 0.3 -j DROP

# Executing the Program

Each GoBackN and SelectiveRepeat has its own Sender and Receiver <br>
They can be compiled by normal javac commands and executed by java commands <br>
When fully executed they output the step by step how packets have dropped <br>
and retransmissions have happened in the logger.txt files and finally a output.txt is <br>
is given which contains the recieved packets data.
