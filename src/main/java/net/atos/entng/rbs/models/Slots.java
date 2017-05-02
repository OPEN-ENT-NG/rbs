package net.atos.entng.rbs.models;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.awt.print.Book;
import java.util.ArrayList;
import java.util.Optional;

public class Slots extends ArrayList<Slot> {

    public Slots() {
        super();
    }

    public Slots(JsonArray slots) {
        slots.forEach(stringSlot -> {
            Slot slot = new Slot(new JsonObject(stringSlot.toString()));
            this.add(slot);
        });
    }

    public boolean areNotStartingAndEndingSameDay() {
        boolean oneFound = false;
        for(Slot slot : this){
            if(slot.isNotStartingAndEndingSameDay()){ oneFound = true;}
        }
        return oneFound;
    }

    public boolean areNotRespectingMinDelay(Optional<Long> minDelay) {
        boolean oneFound = false;
        for(Slot slot : this){
            if((minDelay.isPresent() && minDelay.get() > slot.getDelayFromNowToStart())){ oneFound = true;}
        }
        return oneFound;
    }
    public boolean areNotRespectingMaxDelay(Booking booking, Long maxDelay) {
        boolean oneFound = false;
        for(Slot slot : this){
            if(booking.hasPeriodicEndDate() || booking.getOccurrences(0) != 0){
                for (Slot itSlot : new Slot.SlotIterable(booking, slot)){
                    maxDelay = (BookingDateUtils.tomorrowTimestampSecondsForIana(itSlot.getIana()) -
                            BookingDateUtils.currentTimestampSecondsForIana(itSlot.getIana())) + maxDelay;
                    if(itSlot.getDelayFromNowToEnd() > maxDelay){ oneFound = true;}
                }
            }else{
                maxDelay = (BookingDateUtils.tomorrowTimestampSecondsForIana(slot.getIana()) -
                        BookingDateUtils.currentTimestampSecondsForIana(slot.getIana())) + maxDelay;
                if(slot.getDelayFromNowToEnd() > maxDelay){ oneFound = true;}
            }
        }
        return oneFound;
    }
    public Slot getSlotWithLatestEndDate(){
        Slot slot = this.get(0);
        if(this.size() > 1){
            for(int i =1 ; i< this.size(); i++){
                if(slot.getEnd().isBefore(this.get(i).getEnd())){
                    slot = this.get(i);
                }
            }
        }
        return slot;
    }

    public Slot getSlotWithFirstStartDate(){
        Slot slot = this.get(0);
        if(this.size() > 1){
            for(int i =1 ; i< this.size(); i++){
                if(slot.getStart().isAfter(this.get(i).getStart())){
                    slot = this.get(i);
                }
            }
        }
        return slot;
    }

}
