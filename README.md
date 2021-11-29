# Elevator-Concurrency-Simulator
Class project that involves creating an elevator process and a person process and then running threads of these processes and synchronizing them using
semaphores so they can simulate people getting on and off the elevator in groups.

One Elevator thread will run along with 49 Person threads. The elevator can only hold 7 people at a time. 7 or less people will enter the elevator and once that is done,
the elevator will stop at each floor and people that want to get off there can do so. Once the elevator reaches the top floor, it will return back down to floor 1 and
pick up the next set of people. This process will continue until there are no more people waiting in line.

The program will print statements like "Person 5 entered the elevator" and "Person 5 left at floor 3"
