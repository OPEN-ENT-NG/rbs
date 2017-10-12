/**
 * Convertit la date stockée au fomat "DD/MM/YY HH:mm" au format Date
 * @param date
 * @returns {Date}
 */
function convertStringToDate(date){
    var regexpTemp = /([0-9]{2})\/([0-9]{2})\/([0-9]{2})\s([0-9]{2}):([0-9]{2})/;
    var execRegExpEnDate = regexpTemp.exec(date);
    var dateFormatMongo = "20"+execRegExpEnDate[3]+"-"+execRegExpEnDate[2]+"-"+execRegExpEnDate[1]+"T"+execRegExpEnDate[4]+":"+execRegExpEnDate[5]+":00";
    return new Date(dateFormatMongo);
}

/**
 * Convertit un objet Date au format "DD/MM/YY HH:mm"
 * @param nouvelleDate
 * @returns {string}
 */
function convertDateToString(nouvelleDate){
    var nouvelleDateString ="";
    if(nouvelleDate.getDate()>9){
        nouvelleDateString += nouvelleDate.getDate()+"/";
    }else{
        nouvelleDateString += "0" + nouvelleDate.getDate()+"/";
    }
    var month = nouvelleDate.getMonth() + 1;
    if(month>9){
        nouvelleDateString += month + "/";
    }else{
        nouvelleDateString += "0" + month +"/";
    }
    nouvelleDateString += ((nouvelleDate.getFullYear()+"").substring(2))+" ";
    if(nouvelleDate.getHours()>9){
        nouvelleDateString += nouvelleDate.getHours()+":";
    }else{
        nouvelleDateString += "0" + nouvelleDate.getHours()+":";
    }
    if(nouvelleDate.getMinutes()>9){
        nouvelleDateString += nouvelleDate.getMinutes();
    }else{
        nouvelleDateString += "0" + nouvelleDate.getMinutes();
    }
    return nouvelleDateString;
}
db.timeline.find({$and : [{"type" :"RBS"},{"event-type" : { $in : ["PERIODIC-BOOKING-CREATED","BOOKING-CREATED","PERIODIC-BOOKING-UPDATED","BOOKING-UPDATED"]}}]}).forEach(function(booking) {
    print("ID : " + booking._id);
    var startdateGMT = convertStringToDate(booking.params.startdate);
    print("Begin Date GMT : " + startdateGMT);
    var startdateGMTtemp = new Date(startdateGMT.getTime() -  (startdateGMT.getTimezoneOffset())*60000);
    var startdateUTCString = convertDateToString(startdateGMTtemp);
    print("Begin Date UTC : " + startdateUTCString);
    db.timeline.update({"_id" : booking._id}, { $set : { "params.startdate" : startdateUTCString}});

    var enddateGMT = convertStringToDate(booking.params.enddate);
    print("End Date GMT : " + enddateGMT);
    var enddateGMTtemp = new Date(enddateGMT.getTime() -  (enddateGMT.getTimezoneOffset())*60000);
    var enddateUTCString = convertDateToString(enddateGMTtemp);
    print("End Date UTC : " + enddateUTCString);
    db.timeline.update({"_id" : booking._id}, { $set : { "params.enddate" : enddateUTCString}});

});