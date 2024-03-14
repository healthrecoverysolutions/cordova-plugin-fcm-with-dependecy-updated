#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

const pluginName = 'cordova-plugin-fcm-with-dependecy-updated';

function replaceFileContent(filePath, toReplace, replacementText) {
    if (fs.existsSync(filePath)) {
        const data = fs.readFileSync(filePath).toString();
        const updated = data.replace(toReplace, replacementText);
        fs.writeFileSync(filePath, updated, 'utf8');
    } else {
        console.log(`${pluginName} - replaceFileContent ERROR: file does not exist -> ${filePath}`);
    }
}

function resolveGradleFilePath(directory, baseName) {
    for (const entry of fs.readdirSync(directory, {withFileTypes: true})) {
        if (entry?.isFile() && entry.name.endsWith(baseName)) {
            return path.resolve(directory, entry.name);
        }
    }
    return null;
}

function disableGoogleServicesApplyCall(gradleFilePath) {
    console.log(`plugin ${pluginName} disabling local gradle apply of google services at ${gradleFilePath}`);
    const targetLine = `apply plugin: com.google.gms.googleservices.GoogleServicesPlugin`;
    const replacementText = `// google services apply disabled from plugin preferences`;
    replaceFileContent(gradleFilePath, targetLine, replacementText);
}

function main(context) {
    const projectRoot = context.opts.projectRoot;
    const packagePath = path.resolve(projectRoot, `package.json`);
    const packageData = JSON.parse(fs.readFileSync(packagePath, 'utf8'));
    const cordovaPluginsConfig = packageData?.cordova?.plugins;
    const pluginOpts = cordovaPluginsConfig ? cordovaPluginsConfig[pluginName] : undefined;
    const inheritGoogleServices = pluginOpts?.ANDROID_INHERIT_GOOGLE_SERVICES === 'true';
    console.log(`${pluginName} inheritGoogleServices=${inheritGoogleServices}`);

    if (inheritGoogleServices) {
        const gradleDirectory = path.resolve(projectRoot, `platforms`, `android`, pluginName);
        const gradleFilePath = resolveGradleFilePath(gradleDirectory, `FCMPlugin.gradle`);
        disableGoogleServicesApplyCall(gradleFilePath);
    }
}

module.exports = main;