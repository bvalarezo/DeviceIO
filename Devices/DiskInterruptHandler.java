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
 * The disk interrupt handler. When a disk I/O interrupt occurs, this class is
 * called upon the handle the interrupt.
 * 
 * @OSPProject Devices
 */
public class DiskInterruptHandler extends IflDiskInterruptHandler {
    /**
     * Handles disk interrupts.
     * 
     * This method obtains the interrupt parameters from the interrupt vector. The
     * parameters are IORB that caused the interrupt:
     * (IORB)InterruptVector.getEvent(), and thread that initiated the I/O
     * operation: InterruptVector.getThread(). The IORB object contains references
     * to the memory page and open file object that participated in the I/O.
     * 
     * The method must unlock the page, set its IORB field to null, and decrement
     * the file's IORB count.
     * 
     * The method must set the frame as dirty if it was memory write (but not, if it
     * was a swap-in, check whether the device was SwapDevice)
     * 
     * As the last thing, all threads that were waiting for this event to finish,
     * must be resumed.
     * 
     * @OSPProject Devices
     */
    public void do_handleInterrupt() {
        // MyOut.print(this, "Entering Student Method..." + new Object() {
        // }.getClass().getEnclosingMethod().getName());
        IORB iorb = (IORB) InterruptVector.getEvent(), nextIorb;
        ThreadCB thread = iorb.getThread();
        PageTableEntry page = iorb.getPage();
        OpenFile openFile = iorb.getOpenFile();
        Device device = Device.get(iorb.getDeviceID());
        boolean isSwapDevice = iorb.getDeviceID() == SwapDeviceID;
        FrameTableEntry frame = page.getFrame();
        TaskCB task = thread.getTask();

        /* decrement the iorb count */
        openFile.decrementIORBCount();

        /* try to close the open-file handle */
        if (iorb.getOpenFile().getIORBCount() == 0 && iorb.getOpenFile().closePending)
            iorb.getOpenFile().close();

        /* unlock the page */
        page.unlock();

        /* check if its a swap device */
        if (isSwapDevice) {

            /* check if task is not term */
            if (thread.getStatus() != ThreadCB.ThreadKill && task.getStatus() != TaskCB.TaskTerm)
                frame.setDirty(false);
        } else {
            /* its not swap */

            /* check if thread is not killed */
            if (thread.getStatus() != ThreadCB.ThreadKill && task.getStatus() != TaskCB.TaskTerm)
                frame.setReferenced(true);

            /* check if its a read IO */
            if (iorb.getIOType() == FileRead && task.getStatus() != TaskCB.TaskTerm
                    && thread.getStatus() != ThreadCB.ThreadKill)
                frame.setDirty(true);
        }

        /* check if the task is dead to unreserve the frame */
        if (task.getStatus() == TaskCB.TaskTerm) {
            /* check if frame was reserved */
            if (frame.getReserved() == task)
                frame.setUnreserved(task);
        }

        /* wake up the threads */
        iorb.notifyThreads();

        /* set the device to idle */
        device.setBusy(false);

        /* dequeue a new iorb */
        nextIorb = device.dequeueIORB();

        /* check for non-null */
        if (nextIorb != null)
            device.startIO(nextIorb);

        ThreadCB.dispatch();
    }

    /*
     * Feel free to add methods/fields to improve the readability of your code
     */

}

/*
 * Feel free to add local classes to improve the readability of your code
 */
