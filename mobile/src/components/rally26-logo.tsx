import { Image, type ImageStyle, type StyleProp } from 'react-native';

const LOGO_ASPECT_RATIO = 620 / 160;

export function Rally26Logo({
  width = 196,
  style,
}: {
  width?: number;
  style?: StyleProp<ImageStyle>;
}) {
  return (
    <Image
      source={require('../../assets/images/rally26-logo-light.png')}
      resizeMode="contain"
      accessible
      accessibilityLabel="Rally26"
      style={[{ width, height: width / LOGO_ASPECT_RATIO }, style]}
    />
  );
}
