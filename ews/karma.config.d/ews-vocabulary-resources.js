;(function(config) {
    config.files = config.files || [];
    config.files.push({
        pattern: "kotlin/ews/vocabularies/*.txt",
        included: false,
        served: true,
        watched: false,
        nocache: false
    });
})(config);
