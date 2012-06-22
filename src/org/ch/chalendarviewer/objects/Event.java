package org.ch.chalendarviewer.objects;

import java.util.Calendar;

/**
 * Generic event class
 * @author vitor
 */
public class Event {

    /** Event id */
    protected String mId;
    /** Title */
    protected String mTitle;
    /** Details, description */
    protected String mDetails;
    /** Init date of event */
    protected Calendar mBegin;
    /** Ending date of event */
    protected Calendar mEnd;

    /**
     * Get id 
     * @return id of event
     */
    public String getId() {
        return mId;
    }

    /**
     * Set id 
     * @param id id of event
     */
    public void setId(String id) {
        this.mId = id;
    }

    /**
     * Get title 
     * @return get title of event
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Set title of event
     * @param title event title
     */
    public void setTitle(String title) {
        this.mTitle = title;
    }

    /**
     * Get event details. Description of event
     * @return event details
     */
    public String getDetails() {
        return mDetails;
    }

    /**
     * Set event details
     * @param details event details
     */
    public void setDetails(String details) {
        this.mDetails = details;
    }

    /**
     * Initial date of event
     * @return initial date of event
     */
    public Calendar getBegin() {
        return mBegin;
    }

    /**
     * Set initial date of event
     * @param begin initial date of event
     */
    public void setBegin(Calendar begin) {
        this.mBegin = begin;
    }

    /**
     * Get end date of event
     * @return date of event
     */
    public Calendar getEnd() {
        return mEnd;
    }

    /**
     * Set date of event
     * @param end date of event
     */
    public void setEnd(Calendar end) {
        this.mEnd = end;
    }

}
