document.addEventListener('DOMContentLoaded', function () {
    const chartOptions = {
        series: [{
            name: 'VolumeBar',
            data: []
        }],
        chart: {
            type: 'candlestick',
            height: 350
        },
        title: {
            text: 'Volume Bars',
            align: 'left'
        },
        xaxis: {
            type: 'datetime'
        },
        yaxis: {
            tooltip: {
                enabled: true
            }
        }
    };

    const volumeProfileChartOptions = {
        series: [{
            name: 'Volume',
            data: []
        }],
        chart: {
            type: 'bar',
            height: 350
        },
        plotOptions: {
            bar: {
                horizontal: true,
            }
        },
        title: {
            text: 'Volume Profile',
            align: 'left'
        }
    };

    const chart = new ApexCharts(document.querySelector("#chart"), chartOptions);
    chart.render();

    const volumeProfileChart = new ApexCharts(document.querySelector("#volume-profile-chart"), volumeProfileChartOptions);
    volumeProfileChart.render();

    const obiValueElement = document.getElementById('obi-value');

    const socket = new WebSocket('ws://localhost:7070/data');

    socket.onopen = function(event) {
        console.log("WebSocket connection established.");
    };

    socket.onmessage = function(event) {
        const dashboardEvent = JSON.parse(event.data);
        const volumeBar = dashboardEvent.volumeBar;
        const marketProfile = dashboardEvent.marketProfile;

        // Update OBI display
        obiValueElement.textContent = volumeBar.orderBookImbalance.toFixed(4);

        // Update chart
        const newDataPoint = {
            x: new Date(volumeBar.startTime),
            y: [volumeBar.open, volumeBar.high, volumeBar.low, volumeBar.close]
        };

        chart.appendData([{
            data: [newDataPoint]
        }]);

        // Update volume profile chart
        const volumeProfileData = Object.entries(marketProfile.volumeAtPrice).map(([price, volume]) => {
            return {
                x: price,
                y: volume
            };
        });

        volumeProfileChart.updateSeries([{
            data: volumeProfileData
        }]);
    };

    socket.onclose = function(event) {
        console.log("WebSocket connection closed.");
    };

    socket.onerror = function(error) {
        console.error("WebSocket error:", error);
    };
});
