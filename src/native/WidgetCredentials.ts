import * as FileSystem from 'expo-file-system';
import { Platform } from 'react-native';

const CREDS_FILE = FileSystem.documentDirectory + 'tm_widget_creds.json';

export async function saveWidgetCredentials(baseUrl: string, apiKey: string) {
  if (Platform.OS !== 'android') return;
  try {
    await FileSystem.writeAsStringAsync(CREDS_FILE, JSON.stringify({ baseUrl, apiKey }));
  } catch {}
}

export async function clearWidgetCredentials() {
  if (Platform.OS !== 'android') return;
  try {
    await FileSystem.deleteAsync(CREDS_FILE, { idempotent: true });
  } catch {}
}
