package tv.danmaku.bili.ui.splash.event;

import java.util.List;

/** Test-only fixture for nullable and immutable splash lists. */
public class EventSplashDataList {
    public List<Event> eventList;
    public EventSplashDataList(List<Event> eventList) { this.eventList = eventList; }
    public static class Event {
        private final boolean birthday;
        public Event(boolean birthday) { this.birthday = birthday; }
        public boolean isBirthdayData() { return birthday; }
    }
}
