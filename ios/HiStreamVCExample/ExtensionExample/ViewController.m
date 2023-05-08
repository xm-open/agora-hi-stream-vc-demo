//
//  ViewController.m
//  ExtensionExample
//
//  Created by kuozhi.li on 2023/3/9.
//

#import "ViewController.h"
#import "AppID.h"

#import <AgoraRtcKit/AgoraRtcEngineKit.h>

@interface ViewController ()<AgoraRtcEngineDelegate,
AgoraMediaFilterEventDelegate,
UIPopoverPresentationControllerDelegate>

@property(strong, nonatomic) AgoraRtcEngineKit *agoraKit;
@property(assign, nonatomic) BOOL enable;
@property(weak, nonatomic) IBOutlet UIButton *startVC;
@property(weak, nonatomic) IBOutlet UIButton *stopVC;
@property(weak, nonatomic) IBOutlet UITextField *channelName;
@property(weak, nonatomic) IBOutlet UIButton *joinBtn;
@property(weak, nonatomic) IBOutlet UIButton *extenInit;

@property(assign, nonatomic) BOOL has_join;
@property(assign, nonatomic) NSUInteger uid;
@property(strong, nonatomic) NSString *json_init;
@property(strong, nonatomic) NSString *dir_init;

@property(strong, nonatomic) NSMutableString *pre_result;

@end

@implementation ViewController

//NSString *EXTENSION_NAME = @"agora-hi-streamvc-filter";
NSString *EXTENSION_VENDOR_NAME = @"Ximalaya";
NSString *EXTENSION_AUDIO_FILTER = @"HiStreamVC";

NSString *KEY_INIT_FILTER = @"init_vc";
NSString *KEY_START_VC = @"start_vc";
NSString *KEY_STOP_VC = @"stop_vc";

- (void)touchesBegan:(NSSet *)touches withEvent:(UIEvent *)event
{
    [self.view endEditing:YES];
}

- (void)viewDidLoad {
    self.json_init = [NSBundle.mainBundle pathForResource:@"init" ofType:@"json" inDirectory:@"stream_vc"];
    self.dir_init = [self.json_init stringByDeletingLastPathComponent];
    if (![self.dir_init hasSuffix:@"/"]) {
        self.dir_init = [self.dir_init stringByAppendingString:@"/"];
    }
    self.enable = false;
    self.has_join = false;
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    [self initRtcEngine];
}

- (void)initRtcEngine {
    NSLog(@"appid: %@", appID);
    AgoraRtcEngineConfig *config = [AgoraRtcEngineConfig new];
    config.appId = appID;
    // 监听插件事件，用于接收 onEvent 回调
    config.eventDelegate = self;
    AgoraLogConfig *logConfig = [AgoraLogConfig new];
    logConfig.level = AgoraLogLevelInfo;
    config.logConfig = logConfig;
    self.agoraKit = [AgoraRtcEngineKit sharedEngineWithConfig:config
                                                     delegate:self];
    if(self.agoraKit == nil)
        NSLog(@"agoraKit is nil");
    [self enableExtension:nil];
    int ret = [self.agoraKit enableAudio];
    NSLog(@"enableAudio ret: %d", ret);
    ret = [self.agoraKit setChannelProfile:AgoraChannelProfileLiveBroadcasting];
    NSLog(@"setChannelProfile: %d", ret);
    ret = [self.agoraKit setClientRole:AgoraClientRoleBroadcaster];
    NSLog(@"setClientRole: %d", ret);
//    [self.enableExtensionBtn setEnabled:true];
}

- (IBAction)enableExtension:(id)sender {
    self.enable = !self.enable;
    NSLog(@"vendor_name: %@", EXTENSION_VENDOR_NAME);
    NSLog(@"audio_filter: %@", EXTENSION_AUDIO_FILTER);
    int ret = [self.agoraKit enableExtensionWithVendor:EXTENSION_VENDOR_NAME
                                             extension:EXTENSION_AUDIO_FILTER
                                               enabled:self.enable];
    NSLog(@"enableExtension: %d", ret);
//    if (self.enable) {
//        [self.enableExtensionBtn setTitle:@"disableExtension"
//                                 forState:UIControlStateNormal];
//    } else {
//        [self.enableExtensionBtn setTitle:@"enableExtension"
//                                 forState:UIControlStateNormal];
//    }
//    [self.enableExtensionBtn setEnabled:false];
    
}
- (IBAction)joinChannel:(id)sender {
    //    NSString* channel = channelName.text;
    const NSString *channel = self.channelName.text;
    if(channel.length == 0){
        //        ToastView.show(text: "please input channel name!".localized);
        NSLog(@"please input channel name!");
        UIAlertController* alert = [UIAlertController alertControllerWithTitle:@"Alert"
                                       message:@"请输入频道名"
                                       preferredStyle:UIAlertControllerStyleAlert];
        UIAlertAction* defaultAction = [UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault
           handler:^(UIAlertAction * action) {}];

        [alert addAction:defaultAction];
        [self presentViewController:alert animated:YES completion:nil];
        return;
    }
    
    if(self.has_join){
        int ret = [self.agoraKit leaveChannel:nil]; // callbcak
        NSLog(@"leaveChannel ret: %d", ret);
        [self.joinBtn setTitle:@"加入频道" forState:UIControlStateNormal];
        [self.startVC setEnabled:false];
        [self.stopVC setEnabled:false];
//        [self.enableExtensionBtn setEnabled:false];
        [self.extenInit setEnabled:false];
        self.has_join = false;
    } else {
        NSString *accessToken = token;
        int ret = [self.agoraKit joinChannelByToken:accessToken
                                      channelId:channel
                                           info:nil
                                            uid:0
                                    joinSuccess:^(NSString * _Nonnull channel, NSUInteger uid, NSInteger elapsed) {
            NSLog(@"joinseSuccess");
            self.uid = uid;
        }];
        NSLog(@"joinChannelByToken: %d", ret);
        [self.joinBtn setTitle:@"离开频道" forState:UIControlStateNormal];
        [self.extenInit setEnabled:true];
        self.has_join = true;
    }
    
}

- (IBAction)startVCAction:(id)sender {
    NSLog(@"before start_vc");
    NSError *error;
    NSData *data = [NSJSONSerialization dataWithJSONObject:@{  }
                                                   options:NSJSONWritingPrettyPrinted
                                                     error:&error];
    int ret = [self.agoraKit
               setExtensionPropertyWithVendor:EXTENSION_VENDOR_NAME
               extension:EXTENSION_AUDIO_FILTER
               key:KEY_START_VC
               value:[[NSString alloc]
                      initWithData:data
                      encoding:NSUTF8StringEncoding]];
    NSLog(@"after start_vc: %d", ret);
    [self.stopVC setEnabled:true];
    [self.startVC setEnabled:false];
}

- (IBAction)stopVCAction:(id)sender {
    NSLog(@"before stop_vc");
    NSError *error;
    NSData *data = [NSJSONSerialization dataWithJSONObject:@{  }
                                                   options:NSJSONWritingPrettyPrinted
                                                     error:&error];
    int ret = [self.agoraKit
               setExtensionPropertyWithVendor:EXTENSION_VENDOR_NAME
               extension:EXTENSION_AUDIO_FILTER
               key:KEY_STOP_VC
               value:[[NSString alloc]initWithData:data
                                          encoding:NSUTF8StringEncoding]];
    NSLog(@"after stop_vc: %d", ret);
    [self.stopVC setEnabled:false];
}

- (IBAction)initExtensionAction:(id)sender {
    NSLog(@"before init");
    NSError *error;
    NSData *data = [NSJSONSerialization dataWithJSONObject:@{@"appkey": appkey,
                                                             @"secret": secret,
                                                             @"init_json": self.json_init,
                                                             @"init_dir": self.dir_init
                                                           }
                                                   options:NSJSONWritingPrettyPrinted
                                                     error:&error];
    int ret = [self.agoraKit
               setExtensionPropertyWithVendor:EXTENSION_VENDOR_NAME
               extension:EXTENSION_AUDIO_FILTER
               key:KEY_INIT_FILTER
               value:[[NSString alloc]
                      initWithData:data
                      encoding:NSUTF8StringEncoding]];
    NSLog(@"after init: %d", ret);
}

- (void)onEvent:(NSString *)provider extension:(NSString *)extension key:(NSString *)key value:(NSString *)value {
    NSLog(@"onEvent key: %@, value:%@", key, value);
    if ([key isEqualToString:@"InitOk"]) {
        dispatch_async(dispatch_get_main_queue(), ^{
            [self.startVC setEnabled:true];
            [self.extenInit setEnabled:false];
        });
    } else if ([key isEqualToString:@"InitError"]){
        NSLog(@"InitError: %@", value);
    } else if ([key isEqualToString:@"StopOk"] || [key isEqualToString:@"StopError"] || [key isEqualToString:@"StartError"]){
        dispatch_async(dispatch_get_main_queue(), ^{
            [self.stopVC setEnabled:false];
            [self.startVC setEnabled:false];
            [self.extenInit setEnabled:true];
        });
    } else if ([key isEqualToString:@"StartOk"]){
        dispatch_async(dispatch_get_main_queue(), ^{
            [self.startVC setEnabled:false];
            [self.stopVC setEnabled:true];
        });
    }
}

- (void)onExtensionStopped:(NSString * __nullable)provider
                 extension:(NSString * __nullable)extension NS_SWIFT_NAME(onExtensionStopped(_:extension:)){
    NSLog(@"onExtensionStopped, provider: %@, extension: %@", provider, extension);
}

- (void)onExtensionStarted:(NSString * __nullable)provider
                 extension:(NSString * __nullable)extension NS_SWIFT_NAME(onExtensionStarted(_:extension:)){
    NSLog(@"onExtensionStarted, provider: %@, extension: %@", provider, extension);
}

- (void)onExtensionError:(NSString * __nullable)provider
               extension:(NSString * __nullable)extension
                   error:(int)error
                 message:(NSString * __nullable)message{
    NSLog(@"onExtensionError, error: %d, message: %@", error, message);
}

@end
