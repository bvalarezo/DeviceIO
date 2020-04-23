/**
 * Name: Bryan Valarezo
 * StudentID: 110362410
 * 
 * I pledge my honor that all parts of this project were done by me individually, without 
 * collaboration with anyone, and without consulting any external sources that provide 
 * full or partial solutions to a similar project. 
 * I understand that breaking this pledge will result in an “F” for the entire course.
 */

package osp.Devices;
import java.util.*;
import osp.IFLModules.*;
import osp.Hardware.*;
import osp.Interrupts.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Tasks.*;
import osp.Memory.*;
import osp.FileSys.*;

/**
    The disk interrupt handler.  When a disk I/O interrupt occurs,
    this class is called upon the handle the interrupt.

    @OSPProject Devices
*/
public class DiskInterruptHandler extends IflDiskInterruptHandler
{
    /** 
        Handles disk interrupts. 
        
        This method obtains the interrupt parameters from the 
        interrupt vector. The parameters are IORB that caused the 
        interrupt: (IORB)InterruptVector.getEvent(), 
        and thread that initiated the I/O operation: 
        InterruptVector.getThread().
        The IORB object contains references to the memory page 
        and open file object that participated in the I/O.
        
        The method must unlock the page, set its IORB field to null,
        and decrement the file's IORB count.
        
        The method must set the frame as dirty if it was memory write 
        (but not, if it was a swap-in, check whether the device was 
        SwapDevice)

        As the last thing, all threads that were waiting for this 
        event to finish, must be resumed.

        @OSPProject Devices 
    */
    public void do_handleInterrupt()
    {
        // your code goes here
        // get interrupt (IORB)InterruptVector.getEvent()
        // get iorb
        // get thread
        // get page
        // get openfile handle
        // decrementIORBCount() of OpenFile
        //If OpenFile has closePending and getIORBCount() == 0:
            //close the file(see do_cancelPendingIO)
        // unlock the page
        // if I/O is not a page swap (getDeviceID() != SwapDeviceID)
        // and the thread is not dead
        // and the Task is still alive
            //set the frame as referenced (setReferenced)
            //if Read:
                //set frame as dirty
        //If I/O was swap and task+thread of iorb are alive
            //the frame is set clean
        //if the task is dead (TaskTerm) and frame from iorb was reserved
            //frame must be setUnreserved()
        //notify the threads notfiyThreads()
        //device is set idle
        //device must dequeueIORB()
            //if non null: restart device with iorb
        //dispatch()
        
    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
