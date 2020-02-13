
#import <Foundation/Foundation.h>
#import <Cordova/CDVPlugin.h>
#import <Cordova/CDVPluginResult.h>
#import <AVFoundation/AVFoundation.h>

@interface RadioPlugin : CDVPlugin

@property NSString *callbackId;
@property AVPlayer *streamPlayer;
@property NSInteger volume;
@property NSString *streamUrl;

- (void)initialize:(CDVInvokedUrlCommand*)command;
- (void)play:(CDVInvokedUrlCommand*)command;
- (void)stop:(CDVInvokedUrlCommand*)command;
- (void)setvolume:(CDVInvokedUrlCommand*)command;
@end

@implementation RadioPlugin

#pragma mark Plugin methods

- (void)initialize:(CDVInvokedUrlCommand*)command
{
    NSLog(@"Initialize \n");

    self.callbackId = command.callbackId;
    self.volume = 100;
    self.streamUrl = [command argumentAtIndex:0];

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_NO_RESULT];
    [pluginResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)play:(CDVInvokedUrlCommand*)command
{
    // https://stackoverflow.com/questions/13131177/streaming-mp3-audio-with-avplayer
    // https://developer.apple.com/documentation/avfoundation/AVPlayer?language=objc

    NSLog(@"Play \n");

    // NSString *streamUrl = [command argumentAtIndex:0];
    id volObj = [command argumentAtIndex:1];
    if(volObj) {
        NSInteger volume = [volObj integerValue];
        if (volume >= 0) {
            [self mp_setVolume:volume];
        }
    }

    NSURL *streamNSURL = [NSURL URLWithString:self.streamUrl];

    self.streamPlayer = [[AVPlayer alloc] initWithURL:streamNSURL];
    [self.streamPlayer addObserver:self forKeyPath:@"status" options:0 context:nil];

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
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

- (void)setvolume:(CDVInvokedUrlCommand*)command
{
    NSLog(@"Set volume \n");
    [self mp_setVolume:[[command argumentAtIndex:0] integerValue]];

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
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
            //
            NSLog(@"AVPlayerStatusReadyToPlay");
            self.streamPlayer.volume = self.volume / 100.00;
            [self.streamPlayer play];

            [self mp_sendListenerResult:@"STARTED"];
        } else if (self.streamPlayer.status == AVPlayerItemStatusUnknown) {
            NSLog(@"AVPlayer Unknown");
            // [self mp_sendListenerResult:@"STOPPED"];
        }
    }
}

#pragma mark Private methods
- (void)mp_setVolume:(NSInteger)volume
{
    if (volume > 100) {
        self.volume = 100;
    } else {
        if (volume < 0) {
            self.volume = 0;
        } else {
            self.volume = volume;
        }
    }

    if (self.streamPlayer != nil) {
        self.streamPlayer.volume = self.volume / 100.00;
    }
}

- (void)mp_sendListenerResult:(NSString *)status
{
    if (self.callbackId != nil) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:status];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
    }
}

@end
