//var mementoDatetime = "2010-03-14"
var acceptDatetime = null;
var requestUrl = null;
$(function() {
    $("#list_menu_top").load("/menu_top.html");
    $("#list_footer").load("/menu_bottom.html", function() {
        $("#menu_bottom").center();
    });
    $( window ).resize( function() {
        $("#menu_bottom").center();
        $("#result_wrapper").css("min-height", calculateMinHeightForResults()+16 + "px");
    });
    var adt = null;
    if (acceptDatetime) {
        adt = convertToDate(acceptDatetime);
    }
    if (!adt || isNaN(adt.valueOf())) {
        adt = new Date();
    }
    $("#datepicker").val(getDisplayDate(adt));
    $("#timepicker").val(getDisplayTime(adt));

    $("#url").val(requestUrl);

    $("#search").button()
    .click( function() {
        var date = $("#datepicker").val();
        date += "T" + $("#timepicker").val() + "Z";
        var d = convertToDate(date);
        reloadPage(getMachineDate(d));
    });
    $("#summary").center();
    
    $("#prev_memento").button({
        label: "&#9668"
    });
    $("#next_memento").button({
        label: "&#9658"
    });
    enableSearchOnEnterKey();
    $("#result_wrapper").css("min-height", calculateMinHeightForResults() + "px");


    if (!mementoDistribution) {
        mementoDistribution = {};
    }
    for (archiveId in mementoDistribution) {
        var distribution = mementoDistribution[archiveId];
        var container_id = archiveId + "_timeline";
        createTimeline(distribution, container_id);
    }
});
