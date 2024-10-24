function plotDonutChart(stats, container_id) {

    for (var chart_type in stats) {
        var data = stats[chart_type];
        var container = "#" + chart_type + container_id;
        var width = $(container).width();
        var height = $(container).height();
        var radius = Math.min(width, height) / 2;
        var pie_data = data["total_cc"];

        var arc = d3.svg.arc()
            .outerRadius(radius - 50)
            .innerRadius(radius - 100);

        var pie = d3.layout.pie()
            .sort(null)
            .value(function (d) {
                return d;
            });

        var svg = d3.select(container).append("svg")
            .attr("width", width)
            .attr("height", height)
            .append("g")
            .attr("transform", "translate(" + width / 2 + "," + height / 2 + ")");

        var g = svg.selectAll(".arc")
            .data(pie(pie_data))
            .enter().append("g")
            .attr("class", "arc");

        g.append("path")
            .attr("d", arc)
            .style("fill", function (d, i) {
                return data["colors"][i];
            });

        var legendRectSize = (radius * 0.05);
        var legendSpacing = radius * 0.02;

        var pie_perc = [];
        var sum = 0;
        for (var k = 0, f; f = pie_data[k]; k++) {
            sum += f;
        }
        for (var k = 0, f; f = pie_data[k]; k++) {
            pie_perc[k] = (f / sum) * 100;
        }
        g.append('rect')
            .attr('width', legendRectSize)
            .attr('height', legendRectSize)
            .attr('transform', function (d, i) {
                return donut_legend_transform(d, i, legendRectSize, legendSpacing)
            })
            .style('fill', function (d, i) {
                return data["colors"][i];
            })
            .style('stroke', function (d, i) {
                return data["colors"][i];
            });

        g.append('text')
            .attr('x', legendRectSize + legendSpacing)
            .attr('y', legendRectSize - (legendSpacing - 4))
            .attr('transform', function (d, i) {
                return donut_legend_transform(d, i, legendRectSize, legendSpacing)
            })
            .text(function (d, i) {
                var count;
                if (chart_type == "productivity") {
                    count = "Artifacts"
                }
                else {
                    count = "Interactions"
                }
                return data["labels"][i] + ", " + count + ": " + d.data + ", " + parseFloat(pie_perc[i]).toFixed(2) + "%";
            })
            .style("font-size", "18px");
    }
}

function donut_legend_transform(d, i, legendRectSize, legendSpacing) {
    var height = legendRectSize + legendSpacing;
    var offset =  height * (i+5) / 2;
    var horz = -11 * legendRectSize;
    var vert = i * height - offset + (10*i);
    //console.log(i + ": " + offset + ', translate(' + horz + ',' + vert + ')');
    return 'translate(' + horz + ',' + vert + ')';
}

function updateImpactTable(data) {
    var portal_order = ["MS_Academic", "CrossRef", "Slideshare", "GitHub"];
    for (var k=0,pname; pname=portal_order[k]; k++) {
        var rows = [];
        var top_arts = data[pname];
        var row = "";
        row += "<th class='col-lg-4' style='font-size: 24px; font-weight: 500'>Artifact</th>";
        row += "<th class='col-lg-2' style='font-size: 24px; font-weight: 500'>Interactions</th>";
        rows.push("<tr>" + row + "</tr>");

        for (var i in top_arts["href"]) {
            var row = "";
            row += "<td class='col-lg-4' style='font-size: 18px; font-weight: 300'>" + top_arts["href"][i] + "</td>";
            row += "<td class='col-lg-2' style='font-size: 18px; font-weight: 300'>" + top_arts["total_cc"][i] + "</td>";
            rows.push("<tr>" + row + "</tr>");
        }

        var tbl = '<table class="table table-striped col-lg-6" >' + rows.join("") + '</table>';
        var title = '<h3 class="text-center">Interactions via ' + pname.replace("_", " ") + '</h3>';
        $("#impact_top_5").append("<div>" + title + tbl + "</div>");
    }
}


$(function() {
    plotHeatmapCalendar();
    $.ajax({
        url: "/dashboard/top_artifacts/",
        type: "GET",
        success: function (data, textStatus, jqXHR) {
            updateImpactTable(data);
        }
    });
    $.ajax({
        url: "/dashboard/impact/",
        type: "GET",
        success: function (data, textStatus, jqXHR) {
            plotDonutChart(data, "_chart");
        }
    });

    $.ajax({
        url: "/dashboard/date_range/",
        type: "GET",
        success: function(data, textStatus, jqXHR) {
            plotHeatmapCalendar(data)
        }
     });
});

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
                          console.log(data.date.toUTCString());
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

$(document).ready(function() {
    console.log($("iframe"));
   $("iframe").contents().find(".axis-div text").css("fill", "#000");
});
