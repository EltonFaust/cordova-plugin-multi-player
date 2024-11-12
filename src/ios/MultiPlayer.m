
#import <Foundation/Foundation.h>
#import <Cordova/CDVPlugin.h>
#import <Cordova/CDVPluginResult.h>
#import <AVFoundation/AVFoundation.h>

@interface MultiPlayer : CDVPlugin

@property NSString *callbackId;
@property AVPlayer *streamPlayer;
@property NSString *streamUrl;
@property BOOL connected;

- (void)initialize:(CDVInvokedUrlCommand*)command;
- (void)connect:(CDVInvokedUrlCommand*)command;
- (void)disconnect:(CDVInvokedUrlCommand*)command;
- (void)play:(CDVInvokedUrlCommand*)command;
- (void)stop:(CDVInvokedUrlCommand*)command;
@end

@implementation MultiPlayer

#pragma mark Plugin methods

- (void)initialize:(CDVInvokedUrlCommand*)command
{
    NSLog(@"Initialize \n");

    self.callbackId = command.callbackId;
    self.connected = NO;
    self.streamUrl = [command argumentAtIndex:0];

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_NO_RESULT];
    [pluginResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)connect:(CDVInvokedUrlCommand*)command
{
    NSLog(@"Connect \n");
    // TODO: maybe do something here
    self.connected = YES;

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

    [self mp_sendListenerResult:@"CONNECTED"];
}

- (void)disconnect:(CDVInvokedUrlCommand*)command
{
    NSLog(@"Disconnect \n");
    // TODO: maybe do something here
    self.connected = NO;

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

    [self mp_sendListenerResult:@"DISCONNECTED"];
}

- (void)play:(CDVInvokedUrlCommand*)command
{
    // https://stackoverflow.com/questions/13131177/streaming-mp3-audio-with-avplayer
    // https://developer.apple.com/documentation/avfoundation/AVPlayer?language=objc

    NSLog(@"Play \n");

    if (self.connected == NO) {
        [self connect:command];
    }

    [self mp_sendListenerResult:@"LOADING"];

    NSURL *streamNSURL = [NSURL URLWithString:self.streamUrl];

    self.streamPlayer = [[AVPlayer alloc] initWithURL:streamNSURL];
    [self.streamPlayer addObserver:self forKeyPath:@"status" options:0 context:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(audioInterrupt:) name:AVAudioSessionInterruptionNotification object:nil];

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void) audioInterrupt:(NSNotification*)notification {
    NSNumber *interruptionType = (NSNumber*)[notification.userInfo valueForKey:AVAudioSessionInterruptionTypeKey];
    switch ([interruptionType integerValue]) {
        case AVAudioSessionInterruptionTypeBegan:
            NSLog(@"Stopping...");
            [self.streamPlayer pause];
            [self mp_sendListenerResult:@"STOPPED"];
            break;
        case AVAudioSessionInterruptionTypeEnded:
        {
            if ([(NSNumber*)[notification.userInfo valueForKey:AVAudioSessionInterruptionOptionKey] intValue] == AVAudioSessionInterruptionOptionShouldResume) {
                NSLog(@"Playing...");
                [self.streamPlayer play];
            }
            break;
        }
        default:
            break;
    }
}

- (void)stop:(CDVInvokedUrlCommand*)command
{
    NSLog(@"Stop \n");

    BOOL stopping = NO;

    if (self.streamPlayer != nil) {
        [self.streamPlayer removeObserver:self forKeyPath:@"status" context:nil];
        [self.streamPlayer pause];
        self.streamPlayer = nil;
        stopping = YES;
    }

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

    if (stopping) {
        [self mp_sendListenerResult:@"STOPPED"];
    }
}

#pragma mark Observable to handle player status change

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary *)change context:(void *)context
{
    if (object == self.streamPlayer && [keyPath isEqualToString:@"status"]) {
        if (self.streamPlayer.status == AVPlayerStatusFailed) {
            // Some error occoured
            NSLog(@"AVPlayer Failed");
            [self mp_sendListenerResult:@"ERROR"];
        } else if (self.streamPlayer.status == AVPlayerStatusReadyToPlay) {
            NSLog(@"AVPlayerStatusReadyToPlay");
            [self.streamPlayer play];

            [self mp_sendListenerResult:@"STARTED"];
        } else if (self.streamPlayer.status == AVPlayerItemStatusUnknown) {
            NSLog(@"AVPlayer Unknown");
            // [self mp_sendListenerResult:@"STOPPED"];
        }
    }
}

#pragma mark Private methods

- (void)mp_sendListenerResult:(NSString *)status
{
    if (self.callbackId != nil) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:status];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
    }
}

@end
