// Init
$(function() {
    $("#publons-submit").on('click', function(){
        let params = {
            "username": $("#publons-username").val().trim(),
            "password": $("#publons-password").val().trim()
        }

         $.ajax({
            url: "https://publons.com/api/v2/token/",
            type: "POST",
            data: params,
            success: function(data, textStatus, jqXHR) {
                console.log(data);
                let token = data["token"];
                if (token) {
                    $("#publons-token").html("Token generated: <b>"+token+"</b>");
                    $('#publons-submit').prop('disabled', true);
                }
            }
         });
     });
});
