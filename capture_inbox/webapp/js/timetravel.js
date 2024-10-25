//var mementoDatetime = "2010-03-14"
var acceptDatetime = null;
var requestUrl = null;
$(function() {
    $("#list_menu_top").load("/menu_top.html");
    $("#list_footer").load("/menu_bottom.html", function() {
        //$("#menu_bottom").center();
    });
    $( window ).resize( function() {
        //$("#menu_bottom").center();
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
    if ($("#prev_memento").attr("title") == "Previous: ") {
        $("#prev_memento").button("option", "disabled", true);
    }
    if ($("#next_memento").attr("title") == "Next: ") {
        $("#next_memento").button("option", "disabled", true);
    }
    enableSearchOnEnterKey();
    $("#result_wrapper").css("min-height", calculateMinHeightForResults() + "px");

    /*
    $(".more_mementos_icon").addClass("ui-icon ui-icon-minus");
    $(".more_mementos").next().hide();
    $(".more_mementos_icon").switchClass("ui-icon-minus", "ui-icon-plus");
    $(".more_mementos").first().next().show();
    $($(".more_mementos").first().children()[0]).switchClass("ui-icon-plus", "ui-icon-minus");
    $(".more_mementos").click( function() {
        var sub_results = $(this).next();
        var isOpen = sub_results.is(":visible");
        sub_results[isOpen ? 'slideUp' : 'slideDown']()
        .trigger(isOpen ? 'hide' : 'show');

        if (isOpen) {
            $($(this).children()[0]).switchClass("ui-icon-minus", "ui-icon-plus");
        }
        else {
            $($(this).children()[0]).switchClass("ui-icon-plus", "ui-icon-minus");
        }

    });
    */

});
