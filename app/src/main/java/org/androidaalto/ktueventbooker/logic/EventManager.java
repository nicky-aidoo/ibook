/**
   Copyright: 2011 Android Aalto

   This file is part of BookingRoom.

   BookingRoom is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 3 of the License, or
   (at your option) any later version.

   BookingRoom is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with BookingRoom. If not, see <http://www.gnu.org/licenses/>.
 */

package org.androidaalto.ktueventbooker.logic;

import android.text.format.Time;
import android.util.Log;

import org.androidaalto.ktueventbooker.model.Meeting;
import org.androidaalto.ktueventbooker.model.User;
import org.androidaalto.ktueventbooker.model.db.MeetingDb;
import org.androidaalto.ktueventbooker.model.db.UserDb;
import org.androidaalto.ktueventbooker.validation.ValidationException;
import org.androidaalto.ktueventbooker.validation.ValidationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * @author hannu
 */
public class EventManager {
    private static final String TAG = "EventManager";
    private static final EventInfoValidator validator = new EventInfoValidator();

    private static Set<EventListener> listeners = new HashSet<EventListener>();

    public static EventInfo book(Time start, Time end, String title, String contactName,
                                 String contactMail) throws ValidationException {
        return book(new EventInfo(new UserInfo(contactName, contactMail), start, end, title, generatePin()));
    }

    public static EventInfo bookAsAdmin(Time start, Time end, String title, String contactName,
                                        String contactMail) throws ValidationException {
        return bookAsAdmin(new EventInfo(new UserInfo(contactName, contactMail), start, end, title, generatePin()));
    }

    /**
     * Books the meeting. Synchronously calls <code>onNewMeeting</code> to all
     * the registered listeners.
     * 
     * @param eventInfo The meeting to be booked.
     * @return The meeting stored.
     * @throws ValidationException When the preconditions fail.
     */
    public static EventInfo book(EventInfo eventInfo) throws ValidationException {
        final ValidationResult result = validator.fullValidate(eventInfo);
        if (result.hasErrors())
            throw new ValidationException(result, "There were validation errors in " + eventInfo);
        final EventInfo booked = doBook(eventInfo);
        return booked;
    }

    /**
     * Books the meeting. Synchronously calls <code>onNewMeeting</code> to all
     * the registered listeners.
     * 
     * @param eventInfo The meeting to be booked.
     * @return The meeting stored.
     * @throws ValidationException When the preconditions fail.
     */
    public static EventInfo bookAsAdmin(EventInfo eventInfo) throws ValidationException {
        final ValidationResult result = validator.minimumValidate(eventInfo);
        if (result.hasErrors())
            throw new ValidationException(result, "There were validation errors in " + eventInfo);
        final EventInfo booked = doBook(eventInfo);
        return booked;
    }

    private static EventInfo doBook(EventInfo eventInfo) {
        Log.d(TAG, "Booking: " + eventInfo);
        User user = UserDb.get(eventInfo.getUser().getEmail());
        if (user == null)
            user = UserDb.store(new User(eventInfo.getUser().getName(), eventInfo.getUser()
                    .getEmail()));
        final Meeting meeting = MeetingDb.store(new Meeting(
                user.getId(),
                eventInfo.getTitle(),
                eventInfo.getStart(),
                eventInfo.getEnd(), eventInfo.getPin()));
        final EventInfo booked = new EventInfo(meeting.getId(), null, meeting.getStart(),
                meeting.getEnd(),
                meeting.getTitle(), meeting.getPin());
        triggerOnNewMeetingEvent(booked.getId());
        Log.d(TAG, "Booked: " + booked);
        return booked;
    }

    /**
     * Returns all the meetings from <code>from</code> time to
     * <code>offsetDays</code> forward.
     * 
     * @param from The time from which from the meetings should be returned.
     * @param offsetDays The amount of days forward from the <code>from</code>
     *            parameter the meetings should be returned.
     * @return List of meetings, an empty list if no results found.
     */
    public static List<EventInfo> getMeetings(Time from, int offsetDays) {
        Time end = new Time();
        end.set(from.toMillis(true) + ((long) offsetDays) * 86400000L);

        List<Meeting> meetings = MeetingDb.getMeetings(from, end);
        List<EventInfo> eventInfos = new ArrayList<EventInfo>();
        for (Meeting meeting : meetings) {
            eventInfos.add(new EventInfo(
                    meeting.getId(),
                    null,
                    meeting.getStart(),
                    meeting.getEnd(),
                    meeting.getTitle(), meeting.getPin()));
        }
        Log.d(TAG, "Returning meetings: " + eventInfos);
        return eventInfos;
    }

    /**
     * Returns a meeting with its user data. If no meeting found for id, returns
     * <code>null</code>.
     * 
     * @param id The meeting ID
     * @return The meeting
     */
    public static EventInfo getMeeting(long id) {
        Meeting meeting = MeetingDb.get(id);
        if (meeting == null)
            return null;
        User user = UserDb.get(meeting.getUserId());
        return new EventInfo(meeting.getId(), new UserInfo(user.getId(),
                user.getName(),
                user.getEmail()), meeting.getStart(),
                meeting.getEnd(), meeting
                        .getTitle(), meeting.getPin());
    }

    /**
     * @param id Meeting id
     */
    public static void delete(long id) {
        // Makes sure that the meeting still exists
        Meeting meeting = MeetingDb.get(id);
        if (meeting != null) {
            // Delete
            int i = MeetingDb.delete(id);
            triggerOnDeleteMeetingEvent(id);
            Log.d(TAG, "Deleted rows: " + i);
        }
    }

    public static void addMeetingEventListener(EventListener listener) {
        listeners.add(listener);
    }

    public static void deleteMeetingEventListener(EventListener listener) {
        listeners.remove(listener);
    }

    private static void triggerOnNewMeetingEvent(Long meetingId) {
        for (EventListener listener : listeners) {
            listener.onNewMeeting(meetingId);
        }
    }

    private static void triggerOnDeleteMeetingEvent(Long meetingId) {
        for (EventListener listener : listeners) {
            listener.onDeleteMeeting(meetingId);
        }
    }

    private static void triggerOnEditMeetingEvent(Long meetingId) {
        for (EventListener listener : listeners) {
            listener.onEditMeeting(meetingId);
        }
    }

    /**
     * @param meeting
     */
    public static void update(EventInfo eventInfo) throws ValidationException {
        final ValidationResult result = validator.fullValidate(eventInfo);
        if (result.hasErrors())
            throw new ValidationException(result, "There were validation errors in " + eventInfo);

        UserDb.update(eventInfo.getUser());
        MeetingDb.update(eventInfo);

        triggerOnEditMeetingEvent(eventInfo.getId());
    }
    
    /*
     * Generate random pin from 1000 to 9999
     */
    private static int generatePin() {
        Random rand = new Random();
        int r = rand.nextInt(9000) + 1000;
        Log.d(TAG, "My rand " + r);
        return r;
    }

}
