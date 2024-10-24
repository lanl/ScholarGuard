
var selectedYear = (new Date()).getFullYear();
var selectedPortals = new Set();
var selectedDate = null;

/**
 * Requests filtered data based on click events in the UI
 * Reloads all plots based on reloadPlots
 * @param {Boolean} reloadPlots
 */
function reloadDataView(reloadPlots = false) {
    // Get new data based on parameters (selected components)
    let params = {
        "selected_year": selectedYear
    }
    if (selectedDate) {
        params["selected_date"] = selectedDate;
    }

    if (selectedPortals) {
        params["selected_portals"] = Array.from(selectedPortals).join();
    }

    $.ajax({
        url: "/dashboard/date_range/",
        type: "GET",
        data: params,
        success: function(data, textStatus, jqXHR) {
            // Set activity data and replot charts
            console.log(data);
            if (reloadPlots) {
                plotHeatmapCalendar(data)
            }
        }
     });
}

/**
 * Plots D3 heatmap based on activities
 * @param {Array} data
 */
function plotHeatmapCalendar(data){
    let tooltipUnits = [
        {min: 0, unit: 'activities'},
        {min: 1, max: 1, unit: 'activity'},
        {min: 2, max: 'Infinity', unit: 'activities'}
      ]
    if (data){
        var now = moment().endOf('day').toDate();
        var yearAgo = moment().startOf('day').subtract(1, 'year').toDate();

        var chartData = d3.time.days(yearAgo, now).map(function (dateElement) {
            let formattedDate = moment(dateElement).format('YYYY-MM-DD');
            var count = 0;
            if (formattedDate in data) {
                count = data[formattedDate].count;
            }

          return {
            date: dateElement,
            count: count
          };
        });
        var heatmap = calendarHeatmap()
                        .data(chartData)
                        .selector('.heatmap-cal')
                        .tooltipEnabled(true)
                        .colorRange(['#ebedf0', '#f03b20'])
                        .tooltipUnit(tooltipUnits)
                        .onClick(function (data) {
                            console.log(data);
                            console.log(data.date);
                            console.log(data.date.toISOString());
                            selectedDate = data.date.toISOString().slice(0, 10);
                            selectedYear = data.date.getFullYear();
                            $(".year-select").html(selectedYear + ' <span class="caret"></span>');
                            $(".year-select").val(selectedYear);
                            reloadDataView();
                        });
    } else {
        var heatmap = calendarHeatmap()
            .selector('.heatmap-cal')
            .tooltipEnabled(true)
            .tooltipUnit(tooltipUnits)
            .colorRange(['#ebedf0', '#f03b20']);
    }

    heatmap();  // render the chart
}

/**
 *
 * @param block
 */
function chooseTrackerFilter(block) {
    var t_id = block.attr('id');

    if(!t_id){
        return
    }
    tracker = t_id.split("_", 1)[0]

    if (selectedPortals.has(tracker)) {
        selectedPortals.delete(tracker);
    } else {
        selectedPortals.add(tracker);
    }

    let img = block.find(".filter_img");
    // animate logic
    if (img.hasClass(".filter_img_selected")) {
        img.removeClass(".filter_img_selected");
        img.animate({
            width: "35px",
            height: "35px"
        }, 1);

        block.css("background-color", "white");
    } else {
        img.addClass(".filter_img_selected");
        img.animate({
            width: "25px",
            height: "25px",
            left: img.parent().width() / 2 - img.width() / 2
        }, 1);
        block.css("background-color", "#91efff");
    }

    reloadDataView(reloadPlots = true);
}

/**
 * Helper for setting year select in html
 * @param {Array} data
 */
function setYearSelect(data) {
    out_str = "";
    for (i in data) {
        if (i == 0) {
            $(".year-select").html(data[i].year + ' <span class="caret"></span>');
        }
        out_str += '<li><a value="'+ data[i].year +'" href="#">'+ data[i].year +'</a></li>'
    }
    $(".year-items").html(out_str);
}

/**
 * Set text and activity grid view for AS2 events
 * @param {Array} data
 *
 */
function setActivitySection(data) {

}

// Init
$(function() {
    plotHeatmapCalendar();
    $.ajax({
        url: "/dashboard/date_range/",
        type: "GET",
        success: function(data, textStatus, jqXHR) {
            plotHeatmapCalendar(data)
        }
     });

     $.ajax({
        url: "/dashboard/agg_year_counts/",
        type: "GET",
        success: function(data, textStatus, jqXHR) {
            setYearSelect(data);
        }
     });

    $(".filter_block").click(function(e){
        chooseTrackerFilter($(this));
    });

    $(".year-items").on('click', 'li a', function(){
        let text = $(this).text();
        $(".year-select").html(text + ' <span class="caret"></span>');
        $(".year-select").val(text);
        selectedYear = parseInt(text);
     });
});
