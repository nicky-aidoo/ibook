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

import org.androidaalto.ktueventbooker.model.Meeting;
import org.androidaalto.ktueventbooker.model.db.MeetingDb;
import org.androidaalto.ktueventbooker.validation.FieldError;
import org.androidaalto.ktueventbooker.validation.ObjectError;
import org.androidaalto.ktueventbooker.validation.ValidationResult;
import org.androidaalto.ktueventbooker.validation.Validator;

import java.util.List;
import java.util.regex.Pattern;

/**
 * @author hannu
 */
public class EventInfoValidator implements Validator<EventInfo> {
    private static final int MAX_DAYS = 7;
    private static final long MAX_START_TIME_INCREASE_IN_MILLIS = MAX_DAYS * 24 * 60 * 60 * 1000;
    private static final int MAX_HOURS = 4;
    private static final long MAX_LENGTH_IN_MILLIS = MAX_HOURS * 60 * 60 * 1000;

    private static String ATOM = "[a-z0-9!#$%&'*+/=?^_`{|}~-]";
    private static String DOMAIN = "(" + ATOM + "+(\\." + ATOM + "+)*";
    private static String IP_DOMAIN = "\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\]";

    /**
     * @see org.hibernate.validator.constraints.impl.EmailValidator
     */
    private Pattern pattern = java.util.regex.Pattern.compile(
            "^" + ATOM + "+(\\." + ATOM + "+)*@"
                    + DOMAIN
                    + "|"
                    + IP_DOMAIN
                    + ")$",
            java.util.regex.Pattern.CASE_INSENSITIVE
            );

    @Override
    public ValidationResult fullValidate(EventInfo eventInfo) {
        final ValidationResult errors = new ValidationResult();
        final long nowMillis = System.currentTimeMillis();
        final Time maximumStartingTime = new Time();
        maximumStartingTime.set(nowMillis + MAX_START_TIME_INCREASE_IN_MILLIS);
        if (eventInfo.getStart().after(maximumStartingTime))
            errors.addError(new FieldError(eventInfo, "start", "afterMax",
                    "Starting time too far ahead in the future"));

        final Time maximumEndingTime = new Time();
        maximumEndingTime.set(eventInfo.getStart().toMillis(true) + MAX_LENGTH_IN_MILLIS);
        if (eventInfo.getEnd().after(maximumEndingTime))
            errors.addError(new FieldError(eventInfo, "end", "tooLong",
                    "Event time can't be longer than " + MAX_HOURS + " hours."));

        errors.addAll(minimumValidate(eventInfo));
        return errors;
    }

    public ValidationResult minimumValidate(EventInfo eventInfo) {
        final ValidationResult errors = new ValidationResult();

        if ( eventInfo.getTitle().trim().length() == 0 ) {
            errors.addError(new FieldError(eventInfo, "title", "empty", "Event title is required"));
        }

        final Time now = new Time();
        now.setToNow();
        if (eventInfo.getStart().before(now))
            errors.addError(new FieldError(eventInfo, "start", "beforeNow",
                    "Starting time in past"));

        if (!eventInfo.getStart().before(eventInfo.getEnd()))
            errors.addError(new FieldError(eventInfo, "end", "beforeStart",
                    "Ending time before starting time"));

        UserInfo userInfo = eventInfo.getUser();
        String contactName = userInfo.getName();
        if (contactName == null || contactName.trim().length() == 0) {
            errors.addError(new FieldError(userInfo, "name", "empty", "Contact name is required"));
        }

        String contactMail = userInfo.getEmail();
        if (contactMail == null || contactMail.trim().length() == 0) {
            errors.addError(new FieldError(userInfo, "mail", "empty", "Contact mail is required"));
        } else if (!pattern.matcher(contactMail).matches()) {
            errors.addError(new FieldError(userInfo, "mail", "invalid", "Contact mail is invalid"));
        }

        List<Meeting> meetings = MeetingDb
                .getMeetings(eventInfo.getStart(), eventInfo.getEnd());

        // If there are more than one meeting in that time slot then for sure is
        // an error. The same meeting can't be returned twice from the model
        // layer
        if (meetings.size() > 1) {
            errors.addError(new ObjectError(eventInfo, "clashing", "Clashing Event"));
        } else if (meetings.size() == 1) {
            // In case the current meeting is a new one (it doesn't have id) or
            // if it has it is different than the one returned by the model
            // layer
            if (eventInfo.getId() == null || !eventInfo.getId()
                    .equals(meetings.get(0).getId())) {
                errors.addError(new ObjectError(eventInfo, "clashing", "Clashing Event"));
            }
        }
        return errors;
    }
}
