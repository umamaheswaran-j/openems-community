import { Environment } from "src/environments";

export const environment: Environment = {
    theme: "OpenEMS",

    uiTitle: "EMS UI",
    edgeShortName: "EMS",
    edgeLongName: "Energy Management System",

    backend: 'OpenEMS Edge',
    url: "ws://" + location.hostname + ":9080",

    production: true,
    debugMode: false,
};
