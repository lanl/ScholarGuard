$( function() {
    //$("#menu_top").load("/menu_top.html");
    /*
    $("#footer").load("/menu_bottom.html", function() {
        //$("#menu_bottom").center();
    });
    */

    $(".wrapper").center(true, true);

    $( window ).resize( function() {
        $(".wrapper").center(true, true);
        //$("#menu_bottom").center();
    });

    var adt = randomDate();

    $("#datepicker").val(getDisplayDate(adt));
    $("#timepicker").val(getDisplayTime(adt));

    $("#search").button()
    .click( function() {
        var date = $("#datepicker").val();
        date += "T" + $("#timepicker").val() + "Z";
        var d = convertToDate(date);
        reloadPage(getMachineDate(d));
    });
    $("#nostalgic").button()
        .click( function() {
            window.location.href = "/nostalgic/";
        });

    enableSearchOnEnterKey();
    //initTimeline();
});

function randomDate() {
    var start = new Date(1997, 0, 1);
    var end = new Date();

    return new Date(start.getTime() + Math.random() * (end.getTime() - start.getTime()));
}
