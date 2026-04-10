const {
  withAndroidManifest,
  withAppBuildGradle,
  withProjectBuildGradle,
  withMainApplication,
  withStringsXml,
} = require('@expo/config-plugins');

const withWidget = (config) => {
  config = withAndroidManifest(config, (config) => {
    const app = config.modResults.manifest.application[0];
    if (!app.receiver) app.receiver = [];
    const exists = app.receiver.some(
      (r) => r.$['android:name'] === '.widget.StatusWidgetReceiver'
    );
    if (!exists) {
      app.receiver.push({
        $: {
          'android:name': '.widget.StatusWidgetReceiver',
          'android:exported': 'true',
        },
        'intent-filter': [
          {
            action: [
              { $: { 'android:name': 'android.appwidget.action.APPWIDGET_UPDATE' } },
            ],
          },
        ],
        'meta-data': [
          {
            $: {
              'android:name': 'android.appwidget.provider',
              'android:resource': '@xml/status_widget_info',
            },
          },
        ],
      });
    }
    return config;
  });

  config = withAppBuildGradle(config, (config) => {
    let gradle = config.modResults.contents;

    if (!gradle.includes('glance-appwidget')) {
      gradle = gradle.replace(
        /^(dependencies\s*\{)/m,
        `$1\n    implementation("androidx.glance:glance-appwidget:1.1.1")\n    implementation("androidx.glance:glance-material3:1.1.1")\n    implementation("androidx.compose.material3:material3:1.3.1")\n    implementation("androidx.work:work-runtime-ktx:2.9.1")\n    implementation("androidx.security:security-crypto:1.1.0-alpha06")`
      );
    }

    if (!gradle.includes('buildFeatures')) {
      gradle = gradle.replace(
        /(androidResources\s*\{)/,
        `buildFeatures {\n        compose true\n    }\n    composeOptions {\n        kotlinCompilerExtensionVersion '1.5.15'\n    }\n    $1`
      );
    }

    config.modResults.contents = gradle;
    return config;
  });

  config = withProjectBuildGradle(config, (config) => {
    let gradle = config.modResults.contents;
    gradle = gradle.replace(
      /ndkVersion\s*=\s*["'][\d.]+["']/,
      'ndkVersion = "27.2.12479018"'
    );
    config.modResults.contents = gradle;
    return config;
  });

  config = withMainApplication(config, (config) => {
    let contents = config.modResults.contents;
    if (!contents.includes('WidgetUpdateWorker')) {
      contents = contents.replace(
        'import expo.modules.ApplicationLifecycleDispatcher',
        'import dev.chr0nzz.traefikmanager.widget.WidgetUpdateWorker\nimport expo.modules.ApplicationLifecycleDispatcher'
      );
      contents = contents.replace(
        'ApplicationLifecycleDispatcher.onApplicationCreate(this)',
        'ApplicationLifecycleDispatcher.onApplicationCreate(this)\n    WidgetUpdateWorker.enqueuePeriodicWork(this)'
      );
    }
    config.modResults.contents = contents;
    return config;
  });

  config = withStringsXml(config, (config) => {
    const strings = config.modResults.resources.string || [];
    const exists = strings.some((s) => s.$?.name === 'widget_description');
    if (!exists) {
      strings.push({ $: { name: 'widget_description' }, _: 'Traefik service status' });
      config.modResults.resources.string = strings;
    }
    return config;
  });

  return config;
};

module.exports = withWidget;
