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

/**
    This class stores all pertinent information about a device in
    the device table.  This class should be sub-classed by all
    device classes, such as the Disk class.

    @OSPProject Devices
*/

import osp.IFLModules.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Memory.*;
import osp.FileSys.*;
import osp.Tasks.*;

import java.util.*;

public class Device extends IflDevice {

    private ArrayList<DeviceQueue> queueList;
    private DeviceQueue openQueuePtr;
    private DeviceQueue processingQueuePtr;
    private boolean forwardDirection;

    /**
     * This constructor initializes a device with the provided parameters. As a
     * first statement it must have the following:
     * 
     * super(id,numberOfBlocks);
     * 
     * @param numberOfBlocks -- number of blocks on device
     * 
     * @OSPProject Devices
     */
    public Device(int id, int numberOfBlocks) {
        super(id, numberOfBlocks);
        iorbQueue = new GenericList();
        queueList = new ArrayList<DeviceQueue>();
        /* Init the first of the Queues */
        queueList.add(0, new DeviceQueue(0, true));
        openQueuePtr = queueList.get(0);
        processingQueuePtr = null;
        forwardDirection = true;
    }

    /**
     * This method is called once at the beginning of the simulation. Can be used to
     * initialize static variables.
     * 
     * @OSPProject Devices
     */
    public static void init() {
        // your code goes here

    }

    /**
     * Enqueues the IORB to the IORB queue for this device according to some kind of
     * scheduling algorithm.
     * 
     * This method must lock the page (which may trigger a page fault), check the
     * device's state and call startIO() if the device is idle, otherwise append the
     * IORB to the IORB queue.
     * 
     * @return SUCCESS or FAILURE. FAILURE is returned if the IORB wasn't enqueued
     *         (for instance, locking the page fails or thread is killed). SUCCESS
     *         is returned if the IORB is fine and either the page was valid and
     *         device started on the IORB immediately or the IORB was successfully
     *         enqueued (possibly after causing pagefault pagefault)
     * 
     * @OSPProject Devices
     */
    public int do_enqueueIORB(IORB iorb) {
        // MyOut.print(this, "Entering Student Method..." + new Object() {
        // }.getClass().getEnclosingMethod().getName());
        int retval = FAILURE, blockNumber, cylinder;
        PageTableEntry page = iorb.getPage();
        OpenFile openFile = iorb.getOpenFile();
        ThreadCB thread = iorb.getThread();
        blockNumber = iorb.getBlockNumber();

        /* locking the page */
        if (page.lock(iorb) != SUCCESS)
            return retval;

        /* incrementing the iorb count */
        openFile.incrementIORBCount();

        /* calculate and set the cylinder */
        iorb.setCylinder(getCylinderFromBlockNumber(blockNumber));

        /* Double check the thread status, make sure no SIGKILL */
        if (thread.getStatus() == ThreadCB.ThreadKill)
            return retval;
        else
            retval = SUCCESS;

        /* check if the device is idle */
        if (!isBusy())
            startIO(iorb);
        else {
            /* device is busy */

            /* put the iorb in the queue */
            getOpenQueuePtr().iorbInsert(iorb);
            ((GenericList) iorbQueue).append(iorb);
        }
        return retval;
    }

    /**
     * Selects an IORB (according to some scheduling strategy) and dequeues it from
     * the IORB queue.
     * 
     * @OSPProject Devices
     */
    public IORB do_dequeueIORB() {
        // MyOut.print(this, "Entering Student Method..." + new Object() {
        // }.getClass().getEnclosingMethod().getName());
        DeviceQueue processQ;
        IORB returnIORB;
        /* check if the iorbQueue is empty */
        if (iorbQueue.isEmpty())
            return null;

        /* check if the processing queue ptr is null */
        processQ = getProcessingQueuePtr();
        if (processQ == null) {
            /* close one of the open queues */
            for (DeviceQueue q : queueList) {
                if (!q.isEmpty() && q.isOpen()) {
                    setProcessingQueuePtr(q);
                    processQ = getProcessingQueuePtr();
                    break;
                }
            }
            /* no open queues to process */
            if (processQ == null)
                return null;
        }
        /* check if the processing queue is empty */
        if (processQ.isEmpty()) {
            /* open this processQ */
            processQ.setOpen(true);
            /* switch to a non empty one */
            for (DeviceQueue q : queueList) {
                if (!q.isEmpty() && q.isOpen()) {
                    setProcessingQueuePtr(q);
                    processQ = getProcessingQueuePtr();
                    break;
                }
            }
            /* no non-empty queues to process */
            if (processQ.isEmpty())
                return null;

            /* Flip the direction if at the ends */
            flipDirection();
        }

        /* lets process this Queue */

        /* direction dependent */
        if (isForwardDirection())
            /* Scan forwards */
            returnIORB = (IORB) processQ.removeHead();
        else
            /* Scan backwards */
            returnIORB = (IORB) processQ.removeTail();

        /* lets get the iorb */
        ((GenericList) iorbQueue).remove(returnIORB);
        return returnIORB;
    }

    /**
     * Remove all IORBs that belong to the given ThreadCB from this device's IORB
     * queue
     * 
     * The method is called when the thread dies and the I/O operations it requested
     * are no longer necessary. The memory page used by the IORB must be unlocked
     * and the IORB count for the IORB's file must be decremented.
     * 
     * @param thread thread whose I/O is being canceled
     * 
     * @OSPProject Devices
     */
    public void do_cancelPendingIO(ThreadCB thread) {
        // MyOut.print(this, "Entering Student Method..." + new Object() {
        // }.getClass().getEnclosingMethod().getName());
        Enumeration e;
        IORB ptr;
        /* check if the thread has indeed been Killed */
        if (thread.getStatus() != ThreadCB.ThreadKill)
            return;

        /* iterate through all of the queues */
        for (DeviceQueue q : queueList) {
            e = q.forwardIterator();
            while (e.hasMoreElements()) {
                ptr = (IORB) e.nextElement();
                /* if this iorb is from the killed thread */
                if (ptr.getThread().getID() == thread.getID()) {
                    /* unlock the page */
                    ptr.getPage().unlock();

                    /* decrement the iorb count */
                    ptr.getOpenFile().decrementIORBCount();

                    /* try to close the open-file handle */
                    if (ptr.getOpenFile().getIORBCount() == 0 && ptr.getOpenFile().closePending)
                        ptr.getOpenFile().close();
                    /* remove this iorb from the queue's */
                    q.remove(ptr);
                    ((GenericList) iorbQueue).remove(ptr);
                }
            }
        }
    }

    /**
     * Called by OSP after printing an error message. The student can insert code
     * here to print various tables and data structures in their state just after
     * the error happened. The body can be left empty, if this feature is not used.
     * 
     * @OSPProject Devices
     */
    public static void atError() {
        // your code goes here

    }

    /**
     * Called by OSP after printing a warning message. The student can insert code
     * here to print various tables and data structures in their state just after
     * the warning happened. The body can be left empty, if this feature is not
     * used.
     * 
     * @OSPProject Devices
     */
    public static void atWarning() {
        // your code goes here

    }

    /*
     * Feel free to add methods/fields to improve the readability of your code
     */

    private int getCylinderFromBlockNumber(int blockNumber) {
        // MyOut.print(this, "Entering Student Method..." + new Object() {
        // }.getClass().getEnclosingMethod().getName());
        int offsetBits, blockSize, sectorSize, sectorsPerBlock, sectorsPerTrack, blocksPerTrack, totalCylinders,
                tracksPerCylinders, returnCylinder;
        Disk d = ((Disk) this);
        /* get constants */
        sectorSize = d.getBytesPerSector();
        sectorsPerTrack = d.getSectorsPerTrack();
        tracksPerCylinders = d.getTracksPerPlatter();
        totalCylinders = d.getPlatters();

        /* get the block size */
        offsetBits = MMU.getVirtualAddressBits() - MMU.getPageAddressBits();
        blockSize = (int) Math.pow(2, offsetBits);

        /* get the number of sectors in a block */
        sectorsPerBlock = blockSize / sectorSize;

        /* get the number of blocks in a track */
        blocksPerTrack = sectorsPerTrack / sectorsPerBlock;

        /* calculate the cylinder the block number belongs to */
        returnCylinder = (blockNumber / blocksPerTrack) / totalCylinders;
        return returnCylinder;
    }

    private DeviceQueue getOpenQueuePtr() {
        // MyOut.print(this, "Entering Student Method..." + new Object() {
        // }.getClass().getEnclosingMethod().getName());
        if (this.openQueuePtr.isOpen()) {
            return this.openQueuePtr;
        } else {
            /* find a new open Queue */
            if (findNewOpenQueuePtr())
                return this.openQueuePtr;
            else {
                /* try to open a closed but empty queue */
                for (DeviceQueue q : queueList) {
                    if (q.isEmpty()) {
                        q.setOpen(true);
                    }
                }
                if (findNewOpenQueuePtr())
                    return this.openQueuePtr;
                else {
                    /* create a new queue */
                    DeviceQueue newQ = new DeviceQueue(queueList.get(queueList.size() - 1).getId() + 1, true);
                    queueList.add(newQ);
                    this.openQueuePtr = newQ;
                    return this.openQueuePtr;
                }
            }
        }
    }

    private boolean findNewOpenQueuePtr() {
        // MyOut.print(this, "Entering Student Method..." + new Object() {
        // }.getClass().getEnclosingMethod().getName());
        if (this.openQueuePtr.isOpen())
            return true;
        else {
            for (DeviceQueue q : queueList) {
                if (q.isOpen()) {
                    setOpenQueuePtr(q);
                    return true;
                }
            }
            /* if here; no queue is open */
            return false;
        }
    }

    private void setOpenQueuePtr(DeviceQueue q) {
        this.openQueuePtr = q;
        this.openQueuePtr.setOpen(true);
    }

    private DeviceQueue getProcessingQueuePtr() {
        return processingQueuePtr;
    }

    private void setProcessingQueuePtr(DeviceQueue processingQueuePtr) {
        this.processingQueuePtr = processingQueuePtr;
        this.processingQueuePtr.setOpen(false);
    }

    private boolean isForwardDirection() {
        return forwardDirection;
    }

    private void setForwardDirection(boolean forwardDirection) {
        this.forwardDirection = forwardDirection;
    }

    private void flipDirection() {
        if (isForwardDirection())
            setForwardDirection(false);
        else
            setForwardDirection(true);
    }

    /*
     * Feel free to add local classes to improve the readability of your code
     */
}

class DeviceQueue extends GenericList {

    private int id;
    private boolean open;

    public DeviceQueue(int id, boolean open) {
        super();
        this.id = id;
        this.open = open;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public int getId() {
        return id;
    }

    /**
     * Insert the iorb into the sorted Queue based on the cylinder number
     * 
     * @param iorb the iorb to insert
     * 
     * @OSPProject Devices
     */
    public final synchronized void iorbInsert(IORB iorb) {
        Enumeration e;
        IORB ptr;
        if (isEmpty()) {
            /* make head */
            insert(iorb);
            return;
        } else {
            /* iterate through list and put in sorted position */
            e = forwardIterator(getHead());
            while (e.hasMoreElements()) {
                ptr = (IORB) e.nextElement();
                if (iorb.getCylinder() < ptr.getCylinder()) {
                    prependAtCurrent(iorb);
                    return;
                }
            }
            /* iterated the entire list */
            appendToCurrent(iorb);
            return;
        }

    }

}
