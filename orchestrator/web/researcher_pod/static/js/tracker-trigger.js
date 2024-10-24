
// Init
$(function() {
    $(".resend-capture").on('click', function(){
        let event_id = $(this).attr("id");
        $(".resend-capture-error").text("");
        $(".resend-capture").prop("disabled", true);
        $.ajax({
            url: "/event/"+ event_id +"/capture/",
            type: "GET",
            success: function(data, textStatus, jqXHR) {
                if (data["error"]) {
                    $(".resend-capture-error").text(data["error"]);
                    $(".resend-capture").prop("disabled", false);
                } else{
                    $(".resend-capture-error").text("Successfully sent to Capture.");
                }
            }
        });
    });
});
