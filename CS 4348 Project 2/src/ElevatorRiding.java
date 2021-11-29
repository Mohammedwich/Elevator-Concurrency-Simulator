import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.Random;

public class ElevatorRiding
{	
	static Random rand = new Random();	//destinationFloor generator
	public static int MAX_CAPACITY = 7; // max elevator capacity
	public static int MAX_FLOORS = 8; //number of floors elevator can be on
	public static int NUMBER_OF_PEOPLE = 49;
	public static int numberDispenser = 1; //each person will take a number so first guy to enter is #1, second is #2....
	public static int currentFloor = 1;
	public static int personsEntered = 0; // used to tell elevator when it is ready to go
	public static Integer[] floorDestinationCounts = new Integer[MAX_FLOORS + 1]; //will hold how many people are bound to each floor
			
	public static Semaphore elevatorCapacitySem  = new Semaphore( 0, true ); //caps the number of people entering at a time
	public static Semaphore elevatorCanMoveSem  = new Semaphore( 1, true ); //prevents elevator from moving until it should
	public static Semaphore peopleCanEnterSem  = new Semaphore( 0, true ); //controls when people in line can enter
	public static Semaphore peopleCanLeaveSem  = new Semaphore( 0, true ); //controls when people can start leaving
	public static Semaphore entryProcessSem  = new Semaphore( 1, true ); // one person can do all the stuff involving entering at a time
	public static Semaphore doneBoardingSem  = new Semaphore( 0, true ); //tells main boarding is done
	public static Semaphore leaveSem  = new Semaphore( 1, true ); //lets only one passenger getOff at a time
	public static Semaphore leavingDoneSem = new Semaphore(0, true); //tells main leaving is done
	//the index of current floor will be flooded with permits so all who want a permit to leave can. All will be drained at the end of the elevator run
	public static ArrayList<Semaphore> floorLeavingSem = new ArrayList<Semaphore>(); 
	
	Elevator theElevator = null;
	ArrayList<Person> personsList = null; //list of all the people trying to ride the elevator
	ArrayList<Person> passengerList = null;	//list of people in the elevator
	
	public ElevatorRiding()
	{
		theElevator = new Elevator();
		
		personsList = new ArrayList<Person>();
		passengerList = new ArrayList<Person>(MAX_CAPACITY);
		
		for(int i = 0; i < NUMBER_OF_PEOPLE; i++)
		{
			personsList.add(new Person());
		}
		
		//indexes 2-8 will be accessed an 0-1 not used. Adding 1 to MAX_FLOORS so other things can use currentFloor or destination as the index without arithmetics
		for(int i = 0; i < MAX_FLOORS + 1; i++)
		{
			floorLeavingSem.add(new Semaphore(0, true));
		}
		
	}
	
	public class Elevator implements Runnable
	{
		
		public Elevator()
		{
			
		}

		@Override
		public void run()
		{
			try
			{
				//arrive on floor 1 and wait for people to get on
				elevatorCanMoveSem.acquire();
				currentFloor = 1;
				openDoor(currentFloor);
				
				//reset all floor destination counts so the next group of passengers can increment their floor from 0
				for(int i = 0; i < floorDestinationCounts.length; i++)
				{
					floorDestinationCounts[i] = 0;
				}
				
				elevatorCapacitySem.release(MAX_CAPACITY); // lots of releases so everyone bound for this floor can get off
				peopleCanEnterSem.release(); //lets people enter if floor 1
				elevatorCanMoveSem.acquire(); // wait till it is okay to continue moving
				
				//stop at each floor and wait for people to get off
				while(currentFloor < MAX_FLOORS)
				{
					++currentFloor;
					openDoor( currentFloor ); 
					floorLeavingSem.get(currentFloor).release(NUMBER_OF_PEOPLE); //now everyone waiting to leave on this floor can
					
					//If no passengers are set to leave at this floor then nobody will call getOff() which will signal the elevator to move again
					// therefore, the elevator will only print that it opened on the floor and then move on to the next floor on its own without waiting
					if(floorDestinationCounts[currentFloor] > 0)
					{
						peopleCanLeaveSem.release();
						elevatorCanMoveSem.acquire();
					}	
								
					
				}
				
				//drain all so next run people can't leave until the respective index is flooded with permits again
				for(int i = 0; i < floorLeavingSem.size(); i++)
				{
					floorLeavingSem.get(i).drainPermits();
				}
				
				elevatorCanMoveSem.release(); // so elevator can move back to floor 1 next run
				
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			
		}
		
		public void openDoor(int floor)
		{
			System.out.println("Elevator door opens at floor " + floor);
			System.out.flush();
		}
		
	}
	
	
	public class Person implements Runnable
	{
		int myNumber = 0;
		int destinationFloor = 0;
		boolean inElevator = false;
		
		public Person()
		{
			myNumber = 0;
			destinationFloor = rand.nextInt(7) + 2; //generate a random int between [0, 7) so 0 to 6. Then add 2 so person can go to floors 2-8
			inElevator = false;
		}

		@Override
		public void run()
		{
			try
			{
				elevatorCapacitySem.acquire();
				
				entryProcessSem.acquire();
				//give self a number and increment counter for next person
				myNumber = numberDispenser;
				++numberDispenser;	
				
				//add self to the passengerList and increment the number of people bound for destinationFloor
				passengerList.add(this);
				floorDestinationCounts[destinationFloor]++;
							
				//print entry message and enter elevator then release so next person can do it all too
				printEnterElevator();
				inElevator = true;
				board();			
				entryProcessSem.release();
				
				//wait for elevator to land on the appropriate floor
				floorLeavingSem.get(destinationFloor).acquire(); //blocked from leaving until elevator dispenses permits after opening on floor
				printExitElevator();
				inElevator = false;
				
				leaveSem.acquire();
				getOff(destinationFloor); //does logic for getting off elevator and releasing semaphore when everyone leaves at the current floor
				leaveSem.release();
				
								
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			
		}
		
		public void setNumber(int theNumber)
		{
			myNumber = theNumber;
		}
		
		public void printEnterElevator()
		{
			System.out.println("Person " + myNumber + " enters elevator to go to floor " + destinationFloor);
			System.out.flush();
		}
		
		public void printExitElevator()
		{
			System.out.println("Person " + myNumber + " leaves elevator");
			System.out.flush();
		}
	}

	public void board()
	{
		++personsEntered;
		
		if(personsEntered == MAX_CAPACITY || personsList.size() == 0)
		{ 
			personsEntered = 0;
			doneBoardingSem.release();
		}
	}

	//tells main that people are done leaving so it can tell elevator to move
	public void getOff(int personDestination)
	{
		--floorDestinationCounts[personDestination];
		
		if(floorDestinationCounts[personDestination] == 0)
		{
			leavingDoneSem.release();
		}
	}
	
	
	public static void main(String[] args)
	{
		ElevatorRiding app = new ElevatorRiding();		
		
		ArrayList<Thread> personsThreads = new ArrayList<Thread>();
		
		//fill the list with thread objects initialized with a person
		for(int i = 0; i < app.personsList.size(); i++)
		{
			personsThreads.add(new Thread(app.personsList.get(i)));
		}
		
		
		//run all the people
		for(int i = 0; i < personsThreads.size(); i++)
		{
			personsThreads.get(i).start();
		}
		
		// repeatedly run elevator so it can carry MAX_CAPACITY number of people each time until all persons got carried
		while(app.personsList.size() > 0)
		{
			//Thread state must be "new" otherwise illegalThreadStateException so re-create every loop.
			Thread elevatorThread = new Thread(app.theElevator); 
			elevatorThread.start();
			
			try
			{
				peopleCanEnterSem.acquire(); //wait for elevator to open. People go in on their own. Maybe this sem is unnecessary
				
				doneBoardingSem.acquire(); //wait for people to finish going in
				
				//remove the passengers from personsList
				for(int i = 0; i < app.passengerList.size(); i++)
				{
					for(int j = 0; j < app.personsList.size(); j++)
					{
						//compare their memory address/id/whatever to identify if it is the same instance
						if(app.personsList.get(j) == app.passengerList.get(i))
						{
							app.personsList.remove(j);
							break; //break this inner loop and move on to the next iteration of the outer loop
						}
					}
				}
				
				elevatorCanMoveSem.release();
				
				while(app.passengerList.size() > 0)
				{
					peopleCanLeaveSem.acquire(); //wait for elevator door to open. People leaving on their own. This sem might be unnecessary
					
					//wait for passengers to finish leaving for currentFloor
					leavingDoneSem.acquire(); //released by getOff()
					
					//after passengers get off at the floor, remove them from passengerList and then signal elevator to start moving again
					ArrayList<ElevatorRiding.Person> remainingPassengers = new ArrayList<ElevatorRiding.Person>();
					for(int i = 0; i < app.passengerList.size(); i++)
					{
						if(app.passengerList.get(i).inElevator == true)
						{
							remainingPassengers.add(app.passengerList.get(i) );
						}
					}
					app.passengerList = remainingPassengers;
					
					elevatorCanMoveSem.release();
				}
				
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			
			//wait for elevator thread to die before running it again
			try
			{
				elevatorThread.join();
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		} //while personsList not empty while's end
		
		
		//wait for all person threads to die before ending the program
		try
		{			
			for(int i = 0; i < personsThreads.size(); i++)
			{
				personsThreads.get(i).join();
			}
			
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		
	} //main end

}
