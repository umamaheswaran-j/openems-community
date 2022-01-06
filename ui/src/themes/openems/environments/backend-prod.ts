import { Environment } from "src/environments";

export const environment: Environment = {
    theme: "OpenEMS",

    uiTitle: "EMS UI",
    edgeShortName: "EMS",
    edgeLongName: "Energy Management System",

    backend: 'OpenEMS Backend',
    url: "ws://" + location.hostname + ":9079",

    production: true,
    debugMode: false,
};
