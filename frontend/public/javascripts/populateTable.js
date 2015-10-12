function createTable() {
    alert("createTable triggered!")
        var table = document.getElementById("popularTable");

        var noOfRows = 10;
        var noOfCols = 8;

        for (r = 0; r < noOfRows; r++) {
          var row = table.insertRow(r);
          for (c = 0; c < noOfCols; c++) {
            row.insertCell(c);
          }
        }

        var rows = table.rows;
        var cells = rows[0].cells;
        cells[0].innerHTML = "#OTWOLFromADistance";
        cells[1].innerHTML = "6";


        var header = table.createTHead();
        var row = header.insertRow(0);
        for (c = 0; c < noOfCols; c++) {
          row.insertCell(c);
        }

        row.cells[0].innerHTML = "<b>Most popular hashtags so far</b>";
        row.cells[2].innerHTML = "<b>Most popular hashtags last min.</b>";
        row.cells[4].innerHTML = "<b>Most popular languages so far</b>";
        row.cells[6].innerHTML = "<b>Most popular languages last min.</b>";
        alert("finished createTAble")
}