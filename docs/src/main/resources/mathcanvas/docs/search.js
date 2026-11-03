// The search box: matches what is typed against every heading of the site (search-index.js,
// written by the generator), every word anywhere in the heading or its page's name, and lists the
// first matches. Arrow keys move through them, Enter follows one, Escape closes the list.
(function () {
  "use strict";
  var MAX_RESULTS = 30;

  function start() {
    var input = document.getElementById("search");
    var list = document.getElementById("search-results");
    var index = window.SEARCH_INDEX || [];
    if (!input || !list) {
      return;
    }
    var selected = -1;

    function close() {
      list.hidden = true;
      list.textContent = "";
      selected = -1;
    }

    function select(i) {
      var items = list.children;
      if (items.length === 0) {
        return;
      }
      if (selected >= 0 && selected < items.length) {
        items[selected].classList.remove("selected");
      }
      selected = (i + items.length) % items.length;
      items[selected].classList.add("selected");
      items[selected].scrollIntoView({ block: "nearest" });
    }

    function update() {
      var words = input.value.toLowerCase().split(/\s+/).filter(function (w) {
        return w.length > 0;
      });
      list.textContent = "";
      selected = -1;
      if (words.length === 0) {
        close();
        return;
      }
      var shown = 0;
      for (var i = 0; i < index.length && shown < MAX_RESULTS; i++) {
        var entry = index[i];
        var haystack = (entry.t + " " + entry.p).toLowerCase();
        var all = words.every(function (w) {
          return haystack.indexOf(w) >= 0;
        });
        if (!all) {
          continue;
        }
        var item = document.createElement("li");
        var link = document.createElement("a");
        link.href = entry.h;
        link.textContent = entry.t + " ";
        var page = document.createElement("span");
        page.className = "page";
        page.textContent = entry.p;
        link.appendChild(page);
        item.appendChild(link);
        list.appendChild(item);
        shown++;
      }
      if (shown === 0) {
        var none = document.createElement("li");
        none.className = "page";
        none.textContent = "No heading matches";
        none.style.padding = "0.3rem 0.7rem";
        list.appendChild(none);
      }
      list.hidden = false;
    }

    input.addEventListener("input", update);
    input.addEventListener("keydown", function (event) {
      if (event.key === "Escape") {
        input.value = "";
        close();
      } else if (event.key === "ArrowDown") {
        event.preventDefault();
        select(selected + 1);
      } else if (event.key === "ArrowUp") {
        event.preventDefault();
        select(selected - 1);
      } else if (event.key === "Enter") {
        var target = list.children[selected >= 0 ? selected : 0];
        var link = target && target.querySelector("a");
        if (link) {
          window.location.href = link.href;
          close();
        }
      }
    });
    document.addEventListener("click", function (event) {
      if (!event.target.closest || !event.target.closest(".search")) {
        close();
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start);
  } else {
    start();
  }
})();
