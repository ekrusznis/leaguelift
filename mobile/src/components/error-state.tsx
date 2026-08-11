import Ionicons from '@expo/vector-icons/Ionicons';
import { StyleSheet, View } from 'react-native';

import { Brand, Spacing } from '@/constants/theme';

import { Button } from './button';
import { ThemedText } from './themed-text';

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <View style={styles.container}>
      <Ionicons name="alert-circle-outline" size={28} color={Brand.errorRed} />
      <ThemedText type="small" themeColor="textSecondary" style={styles.message}>
        {message}
      </ThemedText>
      {onRetry && (
        <Button variant="secondary" onPress={onRetry}>
          Try Again
        </Button>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
    paddingVertical: Spacing.five,
  },
  message: {
    textAlign: 'center',
  },
});
