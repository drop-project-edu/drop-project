/*
 * Selection of several rows of a table, to apply an action to all of them at once.
 *
 * The markup drives everything, so a table becomes selectable without any page specific javascript:
 *
 *   <div class="bulk-scope" data-bulk-scope="theFormId">   the element that contains the table and the toolbar
 *   <input type="checkbox" name="ids" form="theFormId" data-bulk-item/>   one per row
 *   <button form="theFormId" data-bulk-action>   disabled while nothing is selected
 *   <span data-bulk-count>   the number of selected rows
 */
(function () {

    /**
     * The checkboxes of the scope, including the ones of the pages that are not being shown, since DataTables
     * detaches those rows from the document (they would not be submitted with the form).
     */
    function items(scope) {
        var table = scope.querySelector('table');
        if (table && window.jQuery && $.fn.dataTable && $.fn.dataTable.isDataTable(table)) {
            return $(table).DataTable().$('input[data-bulk-item]').toArray();
        }
        return [].slice.call(scope.querySelectorAll('input[data-bulk-item]'));
    }

    document.querySelectorAll('[data-bulk-scope]').forEach(function (scope) {

        var form = document.getElementById(scope.getAttribute('data-bulk-scope'));
        if (!form) {
            return;
        }

        function refresh() {
            var selected = items(scope).filter(function (item) { return item.checked; }).length;
            scope.querySelectorAll('[data-bulk-count]').forEach(function (counter) {
                counter.textContent = selected;
            });
            document.querySelectorAll('[data-bulk-action][form="' + form.id + '"]').forEach(function (button) {
                button.disabled = selected === 0;
            });
        }

        // the checkboxes that are not in the document are not submitted, so they go as hidden inputs
        form.addEventListener('submit', function () {
            form.querySelectorAll('input[type="hidden"][name="ids"]').forEach(function (hidden) {
                hidden.remove();
            });
            items(scope).forEach(function (item) {
                if (item.checked && !document.body.contains(item)) {
                    var hidden = document.createElement('input');
                    hidden.type = 'hidden';
                    hidden.name = 'ids';
                    hidden.value = item.value;
                    form.appendChild(hidden);
                }
            });
        });

        scope.addEventListener('change', refresh);
        refresh();
    });
})();
